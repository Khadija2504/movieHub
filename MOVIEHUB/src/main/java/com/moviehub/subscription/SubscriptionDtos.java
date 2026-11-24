package com.moviehub.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.moviehub.booking.payment.PaymentProvider;

import jakarta.validation.constraints.NotNull;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    /** @param maxQuality highest picture height streamed (480, 720, 1080) */
    public record PlanResponse(
            long id,
            String code,
            String name,
            String description,
            BigDecimal price,
            String currency,
            int periodDays,
            int maxQuality,
            int screens,
            List<String> features) {
    }

    /**
     * @param active      it currently gives access to the streaming catalog
     * @param nextPlan    plan billed from the next renewal (after a plan change), or null
     * @param renewsAt    next automatic payment (auto-renew on), or null
     * @param endsAt      last day of access (auto-renew off), or null
     */
    public record SubscriptionResponse(
            long id,
            PlanResponse plan,
            PlanResponse nextPlan,
            UserSubscription.Status status,
            boolean active,
            boolean autoRenew,
            Instant startedAt,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant renewsAt,
            Instant endsAt,
            Instant cancelledAt,
            Instant endedAt,
            UserSubscription.EndReason endReason,
            PaymentProvider paymentProvider) {
    }

    public record PlanRequest(@NotNull(message = "Choose a plan") Long planId) {
    }
}
