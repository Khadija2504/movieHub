package com.moviehub.store;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.booking.BookingDtos.PaymentSession;
import com.moviehub.booking.payment.PaymentGateway;
import com.moviehub.booking.payment.PaymentGateway.Payment;
import com.moviehub.booking.payment.PaymentGateway.PaymentOrder;
import com.moviehub.booking.payment.PaymentGateway.State;
import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.booking.payment.Payments;
import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.VideoSourceRepository;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.BadRequestException;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.common.exception.ExternalServiceException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.library.LibraryItem;
import com.moviehub.library.LibraryRepository;
import com.moviehub.store.StoreDtos.OrderMovie;
import com.moviehub.store.StoreDtos.OrderResponse;
import com.moviehub.subscription.SubscriptionPlan;
import com.moviehub.subscription.SubscriptionService;
import com.moviehub.subscription.UserSubscription;
import com.moviehub.subscription.UserSubscriptionRepository;
import com.moviehub.user.User;
import com.moviehub.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The store: buying and renting movies, paying subscriptions, and renewing them. Payments use the same providers
 * as cinema bookings (Stripe, or the demo provider).
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

    /** Time to complete the checkout. */
    static final Duration PAYMENT_TIME = Duration.ofMinutes(30);
    /** Extra time given to a card payment in progress before it is abandoned. */
    static final Duration PAYMENT_GRACE = Duration.ofMinutes(15);
    private static final int PAGE_SIZE = 20;
    private static final String REFERENCE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreOrderRepository orderRepository;
    private final MovieRepository movieRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final LibraryRepository libraryRepository;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final Payments payments;
    private final TmdbMapper tmdbMapper;
    private final Clock clock = Clock.systemUTC();

    // ---------------------------------------------------------------- new orders

    /** Starts buying or renting a movie. */
    @Transactional
    public OrderResponse orderMovie(long userId, StoreOrder.Kind kind, int tmdbId) {
        if (kind == StoreOrder.Kind.SUBSCRIPTION) {
            throw new BadRequestException("Choose a plan on the plans page to subscribe");
        }
        Movie movie = movieRepository.findByTmdbId(tmdbId)
                .filter(m -> m.isActive() && m.isStreamable() && videoSourceRepository.existsByMovieId(m.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("This movie is not available on MovieHub"));
        var price = kind == StoreOrder.Kind.PURCHASE ? movie.getPurchasePrice() : movie.getRentalPrice();
        if (price == null) {
            throw new BadRequestException(kind == StoreOrder.Kind.PURCHASE ? "This movie is not for sale"
                    : "This movie cannot be rented");
        }
        Instant now = clock.instant();
        Optional<LibraryItem> item = libraryRepository.findByUserIdAndMovieId(userId, movie.getId());
        if (item.isPresent() && item.get().isOwned()) {
            throw new ConflictException("You already own this movie");
        }
        if (kind == StoreOrder.Kind.RENTAL && item.isPresent() && item.get().isRentalActive(now)) {
            throw new ConflictException("You are already renting this movie");
        }
        // A new checkout replaces an unfinished one for the same movie.
        orderRepository.findPendingForMovie(userId, movie.getId()).stream().filter(o -> o.getPaymentId() == null)
                .forEach(o -> o.end(StoreOrder.Status.CANCELLED, now));

        StoreOrder order = StoreOrder.forMovie(newReference(), userRepository.getReferenceById(userId), kind, movie,
                price, payments.currency(), now.plus(PAYMENT_TIME));
        return toResponse(orderRepository.save(order));
    }

    /** Starts a subscription (first period). Renewals are charged by {@link #renewIfDue}. */
    @Transactional
    public OrderResponse orderSubscription(long userId, long planId) {
        SubscriptionPlan plan = subscriptionService.activePlan(planId);
        if (subscriptionService.hasActive(userId)) {
            throw new ConflictException("You already have a subscription. Change your plan from your account.");
        }
        Instant now = clock.instant();
        orderRepository.findPendingSubscriptions(userId).stream().filter(o -> o.getPaymentId() == null)
                .forEach(o -> o.end(StoreOrder.Status.CANCELLED, now));
        StoreOrder order = StoreOrder.forPlan(newReference(), userRepository.getReferenceById(userId), plan, null,
                payments.currency(), now.plus(PAYMENT_TIME));
        return toResponse(orderRepository.save(order));
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public OrderResponse get(long userId, long orderId) {
        return toResponse(owned(userId, orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> history(long userId, int page) {
        return PageResponse.of(orderRepository.findHistory(userId, PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE)),
                this::toResponse);
    }

    // ---------------------------------------------------------------- payment

    @Transactional
    public PaymentSession startPayment(long userId, long orderId) {
        StoreOrder order = owned(userId, orderId);
        Instant now = clock.instant();
        requirePending(order);
        if (order.isPaymentTimeOver(now) && order.getPaymentId() == null) {
            throw new ConflictException("This checkout has expired. Please start again.");
        }
        PaymentGateway gateway = order.getPaymentProvider() != null && order.getPaymentId() != null
                ? payments.of(order.getPaymentProvider())
                : payments.current();
        PaymentOrder paymentOrder = paymentOrder(order, gateway, true);
        Payment payment = gateway.start(paymentOrder, order.getPaymentId());
        order.setPaymentProvider(gateway.provider());
        if (payment.id() != null) {
            order.setPaymentId(payment.id());
        }
        String publishableKey = gateway.provider() == PaymentProvider.STRIPE ? payments.publishableKey() : null;
        return new PaymentSession(gateway.provider(), payment.clientSecret(), publishableKey, order.getAmount(),
                order.getCurrency(), order.getExpiresAt());
    }

    /** Called by the checkout page once paid. A payment still processing leaves the order PENDING. */
    @Transactional
    public OrderResponse confirm(long userId, long orderId) {
        StoreOrder order = owned(userId, orderId);
        Instant now = clock.instant();
        if (order.getStatus() == StoreOrder.Status.PAID) {
            return toResponse(order);
        }
        requirePending(order);
        if (order.getPaymentProvider() == null) {
            throw new BadRequestException("The payment has not been started");
        }
        if (order.getPaymentProvider() == PaymentProvider.DEMO && order.isPaymentTimeOver(now)) {
            throw new ConflictException("This checkout has expired. Please start again.");
        }
        PaymentGateway gateway = payments.of(order.getPaymentProvider());
        Payment payment = gateway.check(paymentOrder(order, gateway, false), order.getPaymentId());
        switch (payment.state()) {
            case SUCCEEDED -> markPaid(order, payment, now);
            case PROCESSING -> {
                // The page polls again.
            }
            default -> throw new ConflictException("The payment was not completed. Please try again.");
        }
        return toResponse(order);
    }

    /** Abandons an unpaid checkout. */
    @Transactional
    public OrderResponse cancel(long userId, long orderId) {
        StoreOrder order = owned(userId, orderId);
        requirePending(order);
        Instant now = clock.instant();
        if (order.getPaymentId() != null && order.getPaymentProvider() != null) {
            PaymentGateway gateway = payments.of(order.getPaymentProvider());
            Payment payment = gateway.check(paymentOrder(order, gateway, false), order.getPaymentId());
            if (payment.state() == State.SUCCEEDED) {
                // Paid in the meantime: deliver instead of cancelling.
                markPaid(order, payment, now);
                return toResponse(order);
            }
            gateway.cancel(order.getPaymentId());
        }
        order.end(StoreOrder.Status.CANCELLED, now);
        return toResponse(order);
    }

    /** Stripe webhook for a store order. Idempotent. */
    @Transactional
    public void onPaymentSucceeded(String paymentId) {
        Optional<StoreOrder> found = orderRepository.findByPaymentId(paymentId);
        if (found.isEmpty()) {
            log.warn("Payment {} succeeded but no store order uses it", paymentId);
            return;
        }
        StoreOrder order = found.get();
        PaymentGateway gateway = payments.of(order.getPaymentProvider());
        Payment payment = gateway.check(paymentOrder(order, gateway, false), paymentId);
        if (payment.state() != State.SUCCEEDED) {
            return;
        }
        Instant now = clock.instant();
        switch (order.getStatus()) {
            case PENDING -> markPaid(order, payment, now);
            case CANCELLED, EXPIRED, FAILED -> {
                log.warn("Order {} was paid after it ended ({}): refunding", order.getReference(), order.getStatus());
                order.refund(gateway.refund(paymentId), now);
            }
            case PAID, REFUNDED -> {
                // Already handled.
            }
        }
    }

    // ---------------------------------------------------------------- jobs

    @Transactional(readOnly = true)
    public List<Long> staleOrderIds(int limit) {
        return orderRepository.findPendingExpiredBefore(clock.instant(), PageRequest.of(0, limit));
    }

    /** Ends one checkout that was not paid in time (asks Stripe first when a card payment was started). */
    @Transactional
    public void expireIfStale(long orderId) {
        StoreOrder order = orderRepository.findDetailedById(orderId).orElse(null);
        Instant now = clock.instant();
        if (order == null || order.getStatus() != StoreOrder.Status.PENDING || !order.isPaymentTimeOver(now)) {
            return;
        }
        if (order.getPaymentId() == null || order.getPaymentProvider() != PaymentProvider.STRIPE) {
            expire(order, now);
            return;
        }
        PaymentGateway gateway = payments.of(PaymentProvider.STRIPE);
        Payment payment = gateway.check(paymentOrder(order, gateway, false), order.getPaymentId());
        switch (payment.state()) {
            case SUCCEEDED -> markPaid(order, payment, now);
            case CANCELED -> expire(order, now);
            case OPEN -> {
                if (now.isAfter(order.getExpiresAt().plus(PAYMENT_GRACE))) {
                    gateway.cancel(order.getPaymentId());
                    expire(order, now);
                }
            }
            case PROCESSING -> {
                // Wait for the bank.
            }
        }
    }

    /**
     * End of a subscription period: charges the saved card for the next one (auto-renew on) or ends the
     * subscription.
     */
    @Transactional
    public void renewIfDue(long subscriptionId) {
        UserSubscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        Instant now = clock.instant();
        if (subscription == null || subscription.getStatus() != UserSubscription.Status.ACTIVE
                || now.isBefore(subscription.getCurrentPeriodEnd())) {
            return;
        }
        if (!subscription.isAutoRenew()) {
            subscription.end(UserSubscription.EndReason.CANCELLED, now);
            return;
        }
        if (orderRepository.existsByStatusAndSubscriptionId(StoreOrder.Status.PENDING, subscription.getId())) {
            return; // A renewal payment is still processing.
        }
        PaymentGateway gateway;
        try {
            gateway = payments.of(subscription.getPaymentProvider() != null ? subscription.getPaymentProvider()
                    : PaymentProvider.DEMO);
        } catch (ExternalServiceException e) {
            log.warn("Subscription {} cannot be renewed: {}", subscriptionId, e.getMessage());
            subscription.end(UserSubscription.EndReason.PAYMENT_FAILED, now);
            return;
        }
        User user = subscription.getUser();
        StoreOrder order = orderRepository.saveAndFlush(StoreOrder.forPlan(newReference(), user,
                subscription.renewalPlan(), subscription, payments.currency(), now.plus(PAYMENT_TIME)));
        order.setPaymentProvider(gateway.provider());
        Payment payment = gateway.chargeSaved(paymentOrder(order, gateway, false), subscription.getPaymentMethodId());
        switch (payment.state()) {
            case SUCCEEDED -> {
                order.markPaid(payment.id(), now);
                subscription.renew(now);
                log.info("Subscription {} renewed ({})", subscriptionId, order.getReference());
            }
            case PROCESSING -> order.setPaymentId(payment.id());
            default -> {
                order.setPaymentId(payment.id());
                order.end(StoreOrder.Status.FAILED, now);
                subscription.end(UserSubscription.EndReason.PAYMENT_FAILED, now);
                log.info("Subscription {} ended: renewal payment declined", subscriptionId);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private void markPaid(StoreOrder order, Payment payment, Instant now) {
        if (payment.amount() == null || payment.amount().compareTo(order.getAmount()) != 0
                || !order.getCurrency().equalsIgnoreCase(payment.currency())
                || !payment.belongsTo(PaymentOrder.STORE, order.getId())) {
            log.error("Payment {} does not match order {} ({} {})", payment.id(), order.getReference(),
                    order.getAmount(), order.getCurrency());
            throw new ConflictException("The payment does not match this order");
        }
        order.markPaid(payment.id(), now);
        deliver(order, payment, now);
    }

    /** Gives the customer what they paid for. */
    private void deliver(StoreOrder order, Payment payment, Instant now) {
        switch (order.getKind()) {
            case PURCHASE, RENTAL -> {
                LibraryItem item = libraryRepository
                        .findByUserIdAndMovieId(order.getUser().getId(), order.getMovie().getId())
                        .orElseGet(() -> new LibraryItem(order.getUser(), order.getMovie()));
                if (item.getKind() != null && item.isOwned()) {
                    // Bought meanwhile (two tabs): this payment is not needed.
                    log.warn("Order {}: movie already owned, refunding", order.getReference());
                    order.refund(payments.of(order.getPaymentProvider()).refund(payment.id()), now);
                    return;
                }
                if (order.getKind() == StoreOrder.Kind.PURCHASE) {
                    item.purchase(order, now);
                } else {
                    item.rent(order, now);
                }
                libraryRepository.save(item);
            }
            case SUBSCRIPTION -> {
                UserSubscription renewing = order.getSubscription();
                if (renewing != null) {
                    // A renewal that was processing.
                    if (renewing.getStatus() == UserSubscription.Status.ACTIVE) {
                        renewing.renew(now);
                    } else {
                        order.refund(payments.of(order.getPaymentProvider()).refund(payment.id()), now);
                    }
                    return;
                }
                order.setSubscription(subscriptionService.activate(order.getUser(), order.getPlan(),
                        order.getPaymentProvider(), payment.paymentMethodId()));
            }
        }
    }

    private void expire(StoreOrder order, Instant now) {
        order.end(StoreOrder.Status.EXPIRED, now);
        UserSubscription renewing = order.getSubscription();
        if (renewing != null && renewing.getStatus() == UserSubscription.Status.ACTIVE
                && !now.isBefore(renewing.getCurrentPeriodEnd())) {
            // The renewal payment never went through.
            renewing.end(UserSubscription.EndReason.PAYMENT_FAILED, now);
        }
    }

    /** @param withCustomer for a new subscription payment: create the provider customer so the card is saved */
    private PaymentOrder paymentOrder(StoreOrder order, PaymentGateway gateway, boolean withCustomer) {
        boolean subscription = order.getKind() == StoreOrder.Kind.SUBSCRIPTION;
        String customerId = null;
        if (subscription && order.getUser() != null) {
            User user = order.getUser();
            customerId = user.getPaymentCustomerId();
            if (withCustomer && gateway.provider() == PaymentProvider.STRIPE) {
                customerId = gateway.customer(user.getId(), customerId);
                user.setPaymentCustomerId(customerId);
            }
        }
        return new PaymentOrder(PaymentOrder.STORE, order.getId(), order.getReference(), order.getAmount(),
                order.getCurrency(), "MovieHub " + order.getReference() + " - " + order.getDescription(), customerId,
                subscription && order.getSubscription() == null);
    }

    private StoreOrder owned(long userId, long orderId) {
        return orderRepository.findDetailedById(orderId).filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private static void requirePending(StoreOrder order) {
        switch (order.getStatus()) {
            case PENDING -> {
            }
            case PAID -> throw new ConflictException("This order is already paid");
            case EXPIRED -> throw new ConflictException("This checkout has expired. Please start again.");
            default -> throw new ConflictException("This order has been " + order.getStatus().name().toLowerCase());
        }
    }

    private String newReference() {
        Set<String> tried = new HashSet<>();
        while (true) {
            StringBuilder sb = new StringBuilder("MO-");
            for (int i = 0; i < 6; i++) {
                sb.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
            }
            String reference = sb.toString();
            if (tried.add(reference) && !orderRepository.existsByReference(reference)) {
                return reference;
            }
        }
    }

    OrderResponse toResponse(StoreOrder o) {
        Movie m = o.getMovie();
        OrderMovie movie = m == null ? null
                : new OrderMovie(m.getTmdbId(), m.getTitle(), tmdbMapper.image(m.getPosterPath(), "w342"),
                        tmdbMapper.image(m.getBackdropPath(), "w780"),
                        m.getReleaseDate() == null ? null : m.getReleaseDate().getYear());
        return new OrderResponse(o.getId(), o.getReference(), o.getKind(), o.getStatus(), o.getDescription(),
                o.getAmount(), o.getCurrency(), o.getCreatedAt(),
                o.getStatus() == StoreOrder.Status.PENDING ? o.getExpiresAt() : null, o.getPaidAt(),
                o.getRefundId() != null, o.getPaymentProvider(), movie,
                o.getPlan() == null ? null : subscriptionService.toPlan(o.getPlan()), o.isRenewal());
    }
}
