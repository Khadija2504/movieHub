package com.moviehub.store;

import java.math.BigDecimal;
import java.time.Instant;

import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.catalog.Movie;
import com.moviehub.common.entity.BaseEntity;
import com.moviehub.subscription.SubscriptionPlan;
import com.moviehub.subscription.UserSubscription;
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
 * Something bought on MovieHub besides cinema tickets: a movie (PURCHASE), a 48-hour rental (RENTAL) or a
 * subscription period (SUBSCRIPTION). Created PENDING, then PAID once the payment succeeded.
 */
@Entity
@Table(name = "store_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOrder extends BaseEntity {

    public enum Kind {
        PURCHASE,
        RENTAL,
        SUBSCRIPTION
    }

    public enum Status {
        PENDING,
        PAID,
        CANCELLED,
        /** Not paid in time. */
        EXPIRED,
        /** A renewal charge was declined. */
        FAILED,
        /** Paid after it had ended: the money was given back. */
        REFUNDED
    }

    private static final String RENEWAL_SUFFIX = " (renewal)";

    @Column(nullable = false, unique = true, length = 12, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", updatable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", updatable = false)
    private SubscriptionPlan plan;

    /** Set for renewals, and on the first payment once the subscription exists. */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private UserSubscription subscription;

    @Column(nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false, precision = 8, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    /** Deadline to pay while PENDING. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 20)
    private PaymentProvider paymentProvider;

    @Setter
    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "refund_id", length = 100)
    private String refundId;

    private StoreOrder(String reference, User user, Kind kind, String description, BigDecimal amount, String currency,
            Instant expiresAt) {
        this.reference = reference;
        this.user = user;
        this.kind = kind;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.expiresAt = expiresAt;
    }

    public static StoreOrder forMovie(String reference, User user, Kind kind, Movie movie, BigDecimal amount,
            String currency, Instant expiresAt) {
        String what = kind == Kind.RENTAL ? " - 48-hour rental" : " - purchase";
        StoreOrder order = new StoreOrder(reference, user, kind, truncate(movie.getTitle() + what), amount, currency,
                expiresAt);
        order.movie = movie;
        return order;
    }

    public static StoreOrder forPlan(String reference, User user, SubscriptionPlan plan, UserSubscription renewing,
            String currency, Instant expiresAt) {
        String what = "MovieHub " + plan.getName() + " - " + plan.getPeriodDays() + " days"
                + (renewing != null ? RENEWAL_SUFFIX : "");
        StoreOrder order = new StoreOrder(reference, user, Kind.SUBSCRIPTION, what, plan.getPrice(), currency,
                expiresAt);
        order.plan = plan;
        order.subscription = renewing;
        return order;
    }

    /** An automatic subscription renewal (charged without the customer). */
    public boolean isRenewal() {
        return kind == Kind.SUBSCRIPTION && description.endsWith(RENEWAL_SUFFIX);
    }

    public boolean isOwnedBy(long userId) {
        return user != null && user.getId() == userId;
    }

    public boolean isPaymentTimeOver(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markPaid(String paymentId, Instant now) {
        this.paymentId = paymentId;
        this.status = Status.PAID;
        this.paidAt = now;
    }

    public void end(Status status, Instant now) {
        this.status = status;
        this.endedAt = now;
    }

    public void refund(String refundId, Instant now) {
        this.refundId = refundId;
        end(Status.REFUNDED, now);
    }

    private static String truncate(String value) {
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
