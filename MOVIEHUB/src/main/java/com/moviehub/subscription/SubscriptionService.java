package com.moviehub.subscription;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.booking.payment.Payments;
import com.moviehub.common.exception.BadRequestException;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.subscription.SubscriptionDtos.PlanResponse;
import com.moviehub.subscription.SubscriptionDtos.SubscriptionResponse;
import com.moviehub.user.User;

import lombok.RequiredArgsConstructor;

/**
 * Plans and the customer's subscription: cancel (auto-renew off), resume, change plan. Payments (first period
 * and renewals) go through {@link com.moviehub.store.StoreService}, which calls {@link #activate} once paid.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final Payments payments;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<PlanResponse> plans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc().stream().map(this::toPlan).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan activePlan(long planId) {
        return planRepository.findById(planId).filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
    }

    /** The running subscription, or else the most recent ended one; empty if the user never subscribed. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> current(long userId) {
        Optional<UserSubscription> active = subscriptionRepository.findActive(userId);
        if (active.isPresent()) {
            return active.map(this::toResponse);
        }
        return subscriptionRepository.findLatest(userId, PageRequest.of(0, 1)).stream().findFirst()
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean hasActive(long userId) {
        return subscriptionRepository.findActive(userId).isPresent();
    }

    /** Turns auto-renew off: access stays until the end of the paid period. */
    @Transactional
    public SubscriptionResponse cancel(long userId) {
        UserSubscription subscription = running(userId);
        if (subscription.isAutoRenew()) {
            subscription.cancel(clock.instant());
        }
        return toResponse(subscription);
    }

    /** Turns auto-renew back on (before the period ends). */
    @Transactional
    public SubscriptionResponse resume(long userId) {
        UserSubscription subscription = running(userId);
        if (!subscription.isAutoRenew()) {
            if (subscription.getPaymentMethodId() == null) {
                throw new BadRequestException("No saved card for this subscription. Subscribe again when it ends.");
            }
            subscription.resume();
        }
        return toResponse(subscription);
    }

    /** Switches plan from the next renewal (no partial refund or extra charge for the current period). */
    @Transactional
    public SubscriptionResponse changePlan(long userId, long planId) {
        UserSubscription subscription = running(userId);
        SubscriptionPlan plan = activePlan(planId);
        subscription.setNextPlan(plan.getId().equals(subscription.getPlan().getId()) ? null : plan);
        return toResponse(subscription);
    }

    /**
     * A first subscription payment succeeded: starts the subscription (or, if one started meanwhile, adds the paid
     * period to it).
     *
     * @return the subscription the payment belongs to
     */
    @Transactional
    public UserSubscription activate(User user, SubscriptionPlan plan, PaymentProvider provider,
            String paymentMethodId) {
        Instant now = clock.instant();
        Optional<UserSubscription> existing = subscriptionRepository.findActive(user.getId());
        if (existing.isPresent()) {
            log.warn("User {} paid a subscription while one was running: extending it", user.getId());
            existing.get().extend();
            return existing.get();
        }
        UserSubscription subscription = new UserSubscription(user, plan, now);
        subscription.setPaymentProvider(provider);
        subscription.setPaymentMethodId(paymentMethodId);
        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public List<Long> dueIds(int limit) {
        return subscriptionRepository.findDueIds(clock.instant(), PageRequest.of(0, limit));
    }

    private UserSubscription running(long userId) {
        UserSubscription subscription = subscriptionRepository.findActive(userId)
                .orElseThrow(() -> new ConflictException("You don't have an active subscription"));
        if (!subscription.isActive(clock.instant()) && !subscription.isAutoRenew()) {
            throw new ConflictException("Your subscription has ended");
        }
        return subscription;
    }

    public PlanResponse toPlan(SubscriptionPlan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPrice(),
                payments.currency(), p.getPeriodDays(), p.getMaxQuality(), p.getScreens(), List.copyOf(p.getFeatures()));
    }

    SubscriptionResponse toResponse(UserSubscription s) {
        boolean running = s.getStatus() == UserSubscription.Status.ACTIVE;
        Instant now = clock.instant();
        boolean active = running && (now.isBefore(s.getCurrentPeriodEnd()) || s.isAutoRenew());
        return new SubscriptionResponse(s.getId(), toPlan(s.getPlan()),
                s.getNextPlan() == null ? null : toPlan(s.getNextPlan()), s.getStatus(), active, s.isAutoRenew(),
                s.getStartedAt(), s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(),
                running && s.isAutoRenew() ? s.getCurrentPeriodEnd() : null,
                running && !s.isAutoRenew() ? s.getCurrentPeriodEnd() : null, s.getCancelledAt(), s.getEndedAt(),
                s.getEndReason(), s.getPaymentProvider());
    }
}
