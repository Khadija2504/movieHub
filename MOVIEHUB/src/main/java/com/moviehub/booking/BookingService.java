package com.moviehub.booking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.booking.BookingDtos.BookedSeat;
import com.moviehub.booking.BookingDtos.BookingResponse;
import com.moviehub.booking.BookingDtos.CreateBookingRequest;
import com.moviehub.booking.BookingDtos.PaymentSession;
import com.moviehub.booking.BookingDtos.TicketCheck;
import com.moviehub.booking.payment.PaymentGateway;
import com.moviehub.booking.payment.PaymentGateway.Payment;
import com.moviehub.booking.payment.PaymentGateway.PaymentOrder;
import com.moviehub.booking.payment.PaymentGateway.State;
import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.booking.payment.Payments;
import com.moviehub.booking.ticket.QrCodes;
import com.moviehub.booking.ticket.TicketCodes;
import com.moviehub.cinema.CinemaMapper;
import com.moviehub.cinema.Screening;
import com.moviehub.cinema.ScreeningRepository;
import com.moviehub.cinema.Seat;
import com.moviehub.cinema.SeatRepository;
import com.moviehub.cinema.SeatType;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.BadRequestException;
import com.moviehub.common.exception.ConflictException;
import com.moviehub.common.exception.ResourceNotFoundException;
import com.moviehub.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cinema bookings: seat hold, payment, cancellation/refund and tickets.
 *
 * <p>Double booking is prevented twice: bookings of a screening are serialised by a row lock on the screening,
 * and a partial unique index on (screening, seat) of active booking seats rejects anything that slips through.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int PAGE_SIZE = 10;
    private static final String REFERENCE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final CinemaMapper cinemaMapper;
    private final Payments payments;
    private final TicketCodes ticketCodes;
    private final QrCodes qrCodes;
    private final Clock clock = Clock.systemUTC();

    public enum Scope {
        UPCOMING,
        PAST
    }

    // ---------------------------------------------------------------- hold

    /** Holds seats for {@link BookingRules#HOLD_TIME} while the customer pays. */
    @Transactional
    public BookingResponse create(long userId, CreateBookingRequest request) {
        Set<Long> seatIds = new LinkedHashSet<>(request.seatIds());
        if (seatIds.size() != request.seatIds().size()) {
            throw new BadRequestException("The same seat was chosen twice");
        }
        Instant now = clock.instant();
        Screening screening = screeningRepository.findByIdForUpdate(request.screeningId())
                .filter(s -> s.getRoom().getCinema().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Screening", request.screeningId()));
        if (!screening.isBookable(now)) {
            throw new BadRequestException("This screening can no longer be booked");
        }

        // Choosing seats again replaces the previous unpaid hold on this screening.
        bookingRepository.findByUserIdAndScreeningIdAndStatus(userId, screening.getId(), BookingStatus.PENDING)
                .stream().filter(b -> b.getPaymentId() == null)
                .forEach(b -> b.cancel(now, null));
        bookingRepository.flush();

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()
                || seats.stream().anyMatch(s -> !s.getRoom().getId().equals(screening.getRoom().getId()))) {
            throw new BadRequestException("Some seats do not belong to this screening's room");
        }
        Set<Long> taken = bookingSeatRepository.findTakenSeatIds(screening.getId());
        List<String> unavailable = seats.stream().filter(s -> taken.contains(s.getId())).map(Seat::label).sorted()
                .toList();
        if (!unavailable.isEmpty()) {
            throw new ConflictException(seatsTakenMessage(unavailable));
        }

        Booking booking = new Booking(newReference(), userRepository.getReferenceById(userId), screening,
                payments.currency(), now.plus(BookingRules.HOLD_TIME));
        seats.stream().sorted((a, b) -> a.label().compareTo(b.label()))
                .forEach(seat -> booking.addSeat(seat,
                        seat.getType() == SeatType.VIP ? screening.getVipPrice() : screening.getPrice()));
        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Some of these seats were just taken. Please choose other seats.");
        }
        return toResponse(booking, now);
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(long userId, Scope scope, int page) {
        Instant now = clock.instant();
        var pageable = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE);
        var bookings = scope == Scope.PAST ? bookingRepository.findPast(userId, now, pageable)
                : bookingRepository.findUpcoming(userId, now, pageable);
        return PageResponse.of(bookings, b -> toResponse(b, now));
    }

    @Transactional(readOnly = true)
    public BookingResponse get(long userId, long bookingId) {
        return toResponse(owned(userId, bookingId), clock.instant());
    }

    @Transactional(readOnly = true)
    public long countUpcoming(long userId) {
        return bookingRepository.countUpcomingConfirmed(userId, clock.instant());
    }

    // ---------------------------------------------------------------- payment

    /** Starts (or resumes) the payment of a held booking. */
    @Transactional
    public PaymentSession startPayment(long userId, long bookingId) {
        Booking booking = owned(userId, bookingId);
        Instant now = clock.instant();
        requirePayable(booking, now);
        if (booking.isHoldExpired(now) && booking.getPaymentId() == null) {
            throw new ConflictException("Your seat hold has expired. Please choose your seats again.");
        }

        PaymentGateway gateway = booking.getPaymentProvider() != null && booking.getPaymentId() != null
                ? payments.of(booking.getPaymentProvider())
                : payments.current();
        Payment payment = gateway.start(order(booking), booking.getPaymentId());
        booking.setPaymentProvider(gateway.provider());
        if (payment.id() != null) {
            booking.setPaymentId(payment.id());
        }
        String publishableKey = gateway.provider() == PaymentProvider.STRIPE ? payments.publishableKey() : null;
        return new PaymentSession(gateway.provider(), payment.clientSecret(), publishableKey,
                booking.getTotalAmount(), booking.getCurrency(), booking.getExpiresAt());
    }

    /**
     * Called by the checkout page once the payment is done: confirms the booking when the provider says it is
     * paid. A Stripe payment still processing leaves the booking PENDING (the webhook or the expiry job finishes it).
     */
    @Transactional
    public BookingResponse confirm(long userId, long bookingId) {
        Booking booking = owned(userId, bookingId);
        Instant now = clock.instant();
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return toResponse(booking, now);
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException(notPendingMessage(booking));
        }
        if (booking.getPaymentProvider() == null) {
            throw new BadRequestException("The payment has not been started");
        }
        if (booking.getPaymentProvider() == PaymentProvider.DEMO && booking.isHoldExpired(now)) {
            throw new ConflictException("Your seat hold has expired. Please choose your seats again.");
        }
        PaymentGateway gateway = payments.of(booking.getPaymentProvider());
        Payment payment = gateway.check(order(booking), booking.getPaymentId());
        switch (payment.state()) {
            case SUCCEEDED -> markPaid(booking, payment, now);
            case PROCESSING -> {
                // Still pending: the page polls again.
            }
            default -> throw new ConflictException("The payment was not completed. Please try again.");
        }
        return toResponse(booking, now);
    }

    /** Stripe webhook: a payment succeeded (possibly after the customer closed the page). Idempotent. */
    @Transactional
    public void onPaymentSucceeded(String paymentId) {
        Optional<Booking> found = bookingRepository.findByPaymentId(paymentId);
        if (found.isEmpty()) {
            log.warn("Payment {} succeeded but no booking uses it", paymentId);
            return;
        }
        Booking booking = found.get();
        Instant now = clock.instant();
        PaymentGateway gateway = payments.of(booking.getPaymentProvider());
        Payment payment = gateway.check(order(booking), paymentId);
        if (payment.state() != State.SUCCEEDED) {
            return;
        }
        switch (booking.getStatus()) {
            case PENDING -> markPaid(booking, payment, now);
            case CANCELLED, EXPIRED -> {
                if (booking.getRefundId() == null) {
                    // Paid after the seats were released: give the money back.
                    log.warn("Booking {} was paid after it ended ({}): refunding", booking.getReference(),
                            booking.getStatus());
                    booking.cancel(booking.getCancelledAt() != null ? booking.getCancelledAt() : now,
                            gateway.refund(paymentId));
                }
            }
            case CONFIRMED -> {
                // Already handled by the checkout page.
            }
        }
    }

    // ---------------------------------------------------------------- cancellation

    /**
     * Customer cancellation: an unpaid hold any time, a paid booking (refunded in full) until
     * {@link BookingRules#CANCELLATION_DEADLINE} before the screening.
     */
    @Transactional
    public BookingResponse cancel(long userId, long bookingId) {
        Booking booking = owned(userId, bookingId);
        Instant now = clock.instant();
        switch (booking.getStatus()) {
            case PENDING -> booking.cancel(now, settlePendingPayment(booking));
            case CONFIRMED -> {
                Instant deadline = cancellationDeadline(booking);
                if (!now.isBefore(deadline)) {
                    throw new BadRequestException("Bookings can only be cancelled up to "
                            + BookingRules.CANCELLATION_DEADLINE.toHours() + " hours before the screening");
                }
                booking.cancel(now, payments.of(booking.getPaymentProvider()).refund(booking.getPaymentId()));
            }
            default -> throw new ConflictException(notPendingMessage(booking));
        }
        return toResponse(booking, now);
    }

    /** The screening was cancelled by the cinema: every booking is cancelled and paid ones are refunded. */
    @Transactional
    public int cancelForScreening(long screeningId) {
        Instant now = clock.instant();
        List<Booking> open = bookingRepository.findOpenByScreening(screeningId);
        for (Booking booking : open) {
            String refund = booking.getStatus() == BookingStatus.CONFIRMED
                    ? payments.of(booking.getPaymentProvider()).refund(booking.getPaymentId())
                    : settlePendingPayment(booking);
            booking.cancel(now, refund);
        }
        if (!open.isEmpty()) {
            log.info("Screening {} cancelled: {} booking(s) cancelled", screeningId, open.size());
        }
        return open.size();
    }

    // ---------------------------------------------------------------- expiry

    /** Ends one hold that ran out (called by {@link BookingExpiryJob}, one transaction per booking). */
    @Transactional
    public void expireIfStale(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        Instant now = clock.instant();
        if (booking == null || booking.getStatus() != BookingStatus.PENDING || !booking.isHoldExpired(now)) {
            return;
        }
        if (booking.getPaymentId() == null || booking.getPaymentProvider() != PaymentProvider.STRIPE) {
            booking.expire();
            return;
        }
        // A card payment was started: ask Stripe before releasing the seats.
        PaymentGateway gateway = payments.of(PaymentProvider.STRIPE);
        Payment payment = gateway.check(order(booking), booking.getPaymentId());
        boolean graceOver = now.isAfter(booking.getExpiresAt().plus(BookingRules.PAYMENT_GRACE));
        switch (payment.state()) {
            case SUCCEEDED -> markPaid(booking, payment, now);
            case CANCELED -> booking.expire();
            case OPEN -> {
                if (graceOver) {
                    gateway.cancel(booking.getPaymentId());
                    booking.expire();
                }
            }
            case PROCESSING -> {
                // Wait for Stripe's answer: seats stay held.
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> staleHoldIds(int limit) {
        return bookingRepository.findPendingExpiredBefore(clock.instant(), PageRequest.of(0, limit)).stream()
                .map(Booking::getId).toList();
    }

    // ---------------------------------------------------------------- tickets

    /** QR code PNG of a confirmed booking. */
    @Transactional(readOnly = true)
    public byte[] ticketQr(long userId, long bookingId) {
        Booking booking = owned(userId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only confirmed bookings have a ticket");
        }
        return qrCodes.png(ticketCodes.issue(booking.getReference()), QrCodes.DEFAULT_SIZE);
    }

    /** Checks a scanned ticket without changing anything. */
    @Transactional(readOnly = true)
    public TicketCheck verifyTicket(String code) {
        return checkTicket(code, false);
    }

    /** Validates a ticket at the entrance: a ticket can be used once. */
    @Transactional
    public TicketCheck checkIn(String code) {
        return checkTicket(code, true);
    }

    private TicketCheck checkTicket(String code, boolean checkIn) {
        Instant now = clock.instant();
        Optional<Booking> found = ticketCodes.verify(code).flatMap(bookingRepository::findDetailedByReference);
        if (found.isEmpty()) {
            return new TicketCheck(false, "Invalid ticket code", null);
        }
        Booking booking = found.get();
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return new TicketCheck(false, "This booking is " + booking.getStatus().name().toLowerCase(),
                    toResponse(booking, now));
        }
        if (booking.getCheckedInAt() != null) {
            return new TicketCheck(false, "This ticket has already been used", toResponse(booking, now));
        }
        if (checkIn) {
            booking.checkIn(now);
        }
        return new TicketCheck(true, checkIn ? "Checked in" : "Valid ticket", toResponse(booking, now));
    }

    // ---------------------------------------------------------------- helpers

    private Booking owned(long userId, long bookingId) {
        return bookingRepository.findDetailedById(bookingId).filter(b -> b.isOwnedBy(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    private void requirePayable(Booking booking, Instant now) {
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new ConflictException("This booking is already paid");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException(notPendingMessage(booking));
        }
        if (!booking.getScreening().isBookable(now)) {
            throw new ConflictException("This screening can no longer be booked");
        }
    }

    private void markPaid(Booking booking, Payment payment, Instant now) {
        if (payment.amount() == null || payment.amount().compareTo(booking.getTotalAmount()) != 0
                || !booking.getCurrency().equalsIgnoreCase(payment.currency())
                || !payment.belongsTo(PaymentOrder.BOOKING, booking.getId())) {
            log.error("Payment {} does not match booking {} ({} {})", payment.id(), booking.getReference(),
                    booking.getTotalAmount(), booking.getCurrency());
            throw new ConflictException("The payment does not match this booking");
        }
        booking.setPaymentId(payment.id());
        booking.confirm(now);
    }

    /** Before cancelling an unpaid booking: abandon its payment, or refund it if it went through meanwhile. */
    private String settlePendingPayment(Booking booking) {
        if (booking.getPaymentId() == null || booking.getPaymentProvider() == null) {
            return null;
        }
        PaymentGateway gateway = payments.of(booking.getPaymentProvider());
        Payment payment = gateway.check(order(booking), booking.getPaymentId());
        if (payment.state() == State.SUCCEEDED) {
            return gateway.refund(booking.getPaymentId());
        }
        gateway.cancel(booking.getPaymentId());
        return null;
    }

    private PaymentOrder order(Booking booking) {
        Screening s = booking.getScreening();
        String description = "MovieHub " + booking.getReference() + " - " + s.getMovie().getTitle() + " - "
                + s.getRoom().getCinema().getName();
        return new PaymentOrder(booking.getId(), booking.getReference(), booking.getTotalAmount(),
                booking.getCurrency(), description);
    }

    private static Instant cancellationDeadline(Booking booking) {
        return booking.getScreening().getStartsAt().minus(BookingRules.CANCELLATION_DEADLINE);
    }

    private static String notPendingMessage(Booking booking) {
        return switch (booking.getStatus()) {
            case EXPIRED -> "Your seat hold has expired. Please choose your seats again.";
            case CANCELLED -> "This booking has been cancelled";
            case CONFIRMED -> "This booking is already paid";
            case PENDING -> "This booking is waiting for payment";
        };
    }

    private static String seatsTakenMessage(List<String> labels) {
        return labels.size() == 1
                ? "Seat " + labels.get(0) + " was just taken. Please choose another seat."
                : "Seats " + String.join(", ", labels) + " were just taken. Please choose other seats.";
    }

    private String newReference() {
        Set<String> tried = new HashSet<>();
        while (true) {
            StringBuilder sb = new StringBuilder("MH-");
            for (int i = 0; i < 6; i++) {
                sb.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
            }
            String reference = sb.toString();
            if (tried.add(reference) && !bookingRepository.existsByReference(reference)) {
                return reference;
            }
        }
    }

    BookingResponse toResponse(Booking b, Instant now) {
        Screening s = b.getScreening();
        List<BookedSeat> seats = b.getSeats().stream()
                .map(bs -> new BookedSeat(bs.getSeat().getId(), bs.getLabel(), bs.getType(), bs.getPrice())).toList();
        Instant cancellableUntil = switch (b.getStatus()) {
            case PENDING -> s.getStartsAt().isAfter(now) ? s.getStartsAt() : null;
            case CONFIRMED -> now.isBefore(cancellationDeadline(b)) ? cancellationDeadline(b) : null;
            default -> null;
        };
        boolean confirmed = b.getStatus() == BookingStatus.CONFIRMED;
        return new BookingResponse(b.getId(), b.getReference(), b.getStatus(),
                cinemaMapper.toDetails(s, s.isBookable(now)), seats, b.getTotalAmount(), b.getCurrency(),
                b.getCreatedAt(), b.getStatus() == BookingStatus.PENDING ? b.getExpiresAt() : null, b.getPaidAt(),
                b.getCancelledAt(), b.getRefundId() != null, b.getPaymentProvider(), cancellableUntil,
                confirmed ? ticketCodes.issue(b.getReference()) : null, b.getCheckedInAt());
    }
}
