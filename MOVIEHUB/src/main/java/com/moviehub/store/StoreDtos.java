package com.moviehub.store;

import java.math.BigDecimal;
import java.time.Instant;

import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.subscription.SubscriptionDtos.PlanResponse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class StoreDtos {

    private StoreDtos() {
    }

    /**
     * Buy or rent a movie.
     *
     * @param movieId TMDB id, as in /movies/{id}
     */
    public record MovieOrderRequest(
            @NotNull(message = "Choose purchase or rental") StoreOrder.Kind kind,
            @NotNull @Positive Integer movieId) {
    }

    /** The movie of a purchase or rental. {@code tmdbId} is used in /movies/{id} and /watch/{id}. */
    public record OrderMovie(int tmdbId, String title, String posterUrl, String backdropUrl, Integer year) {
    }

    /**
     * @param expiresAt deadline to pay (PENDING only)
     * @param renewal   automatic subscription renewal
     */
    public record OrderResponse(
            long id,
            String reference,
            StoreOrder.Kind kind,
            StoreOrder.Status status,
            String description,
            BigDecimal amount,
            String currency,
            Instant createdAt,
            Instant expiresAt,
            Instant paidAt,
            boolean refunded,
            PaymentProvider paymentProvider,
            OrderMovie movie,
            PlanResponse plan,
            boolean renewal) {
    }
}
