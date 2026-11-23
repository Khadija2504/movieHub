package com.moviehub.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.cinema.CinemaDtos.ScreeningDetails;
import com.moviehub.cinema.SeatType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull(message = "Screening is required") Long screeningId,
            @NotEmpty(message = "Choose at least one seat")
            @Size(max = BookingRules.MAX_SEATS, message = "You can book up to " + BookingRules.MAX_SEATS + " seats at once")
            List<@NotNull Long> seatIds) {
    }

    public record BookedSeat(long seatId, String label, SeatType type, BigDecimal price) {
    }

    /**
     * @param expiresAt        end of the seat hold (PENDING only)
     * @param cancellableUntil last moment the customer can cancel, null when it no longer can
     * @param ticketCode       content of the QR code (CONFIRMED only)
     */
    public record BookingResponse(
            long id,
            String reference,
            BookingStatus status,
            ScreeningDetails screening,
            List<BookedSeat> seats,
            BigDecimal total,
            String currency,
            Instant createdAt,
            Instant expiresAt,
            Instant paidAt,
            Instant cancelledAt,
            boolean refunded,
            PaymentProvider paymentProvider,
            Instant cancellableUntil,
            String ticketCode,
            Instant checkedInAt) {
    }

    /**
     * What the checkout page needs to take the payment.
     *
     * @param clientSecret   Stripe PaymentIntent client secret (STRIPE only)
     * @param publishableKey Stripe publishable key (STRIPE only)
     */
    public record PaymentSession(
            PaymentProvider provider,
            String clientSecret,
            String publishableKey,
            BigDecimal amount,
            String currency,
            Instant expiresAt) {
    }

    public record TicketCodeRequest(@NotBlank(message = "Ticket code is required") String code) {
    }

    /** Result of scanning a ticket at the cinema entrance. */
    public record TicketCheck(boolean valid, String message, BookingResponse booking) {
    }
}
