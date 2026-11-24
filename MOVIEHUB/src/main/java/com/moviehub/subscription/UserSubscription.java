package com.moviehub.subscription;

import java.time.Instant;

import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.common.entity.BaseEntity;
import com.moviehub.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user's subscription. It runs in paid periods: at the end of a period it is renewed (auto-renew on, payment
 * succeeded) or it ends. Cancelling only turns auto-renew off: the paid period is kept.
 */
@Entity
@Table(name = "user_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSubscription extends BaseEntity {

    public enum Status {
        ACTIVE,
        EXPIRED
    }

    public enum EndReason {
        /** Auto-renew was off at the end of the period. */
        CANCELLED,
        /** The renewal payment was declined. */
        PAYMENT_FAILED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    /** Plan of the next period, when the customer switched plans. */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_plan_id")
    private SubscriptionPlan nextPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private EndReason endReason;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 20)
    private PaymentProvider paymentProvider;

    /** Saved card charged on renewal. */
    @Setter
    @Column(name = "payment_method_id", length = 100)
    private String paymentMethodId;

    public UserSubscription(User user, SubscriptionPlan plan, Instant now) {
        this.user = user;
        this.plan = plan;
        this.startedAt = now;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = now.plus(plan.period());
    }

    public boolean isActive(Instant now) {
        return status == Status.ACTIVE && now.isBefore(currentPeriodEnd);
    }

    /** The plan billed at the next renewal. */
    public SubscriptionPlan renewalPlan() {
        return nextPlan != null ? nextPlan : plan;
    }

    /** Starts the next paid period (with the new plan, if one was chosen). */
    public void renew(Instant now) {
        plan = renewalPlan();
        nextPlan = null;
        // Late renewal (server down): the new period starts now, not in the past.
        currentPeriodStart = currentPeriodEnd.isAfter(now) ? currentPeriodEnd : now;
        currentPeriodEnd = currentPeriodStart.plus(plan.period());
    }

    /** Paid again while still running (should not happen, but never lose paid days). */
    public void extend() {
        currentPeriodEnd = currentPeriodEnd.plus(plan.period());
    }

    public void cancel(Instant now) {
        autoRenew = false;
        cancelledAt = now;
    }

    public void resume() {
        autoRenew = true;
        cancelledAt = null;
    }

    public void end(EndReason reason, Instant now) {
        status = Status.EXPIRED;
        endReason = reason;
        endedAt = now;
        nextPlan = null;
    }

    public boolean isOwnedBy(long userId) {
        return user != null && user.getId() == userId;
    }
}
