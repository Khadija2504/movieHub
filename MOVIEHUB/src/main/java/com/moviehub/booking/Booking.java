package com.moviehub.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.moviehub.booking.payment.PaymentProvider;
import com.moviehub.cinema.Screening;
import com.moviehub.cinema.Seat;
import com.moviehub.common.entity.BaseEntity;
import com.moviehub.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @Column(nullable = false, unique = true, length = 12, updatable = false)
    private String reference;

    /** Null once the customer deleted their account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false)
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

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

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refund_id", length = 100)
    private String refundId;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("label")
    private List<BookingSeat> seats = new ArrayList<>();

    public Booking(String reference, User user, Screening screening, String currency, Instant expiresAt) {
        this.reference = reference;
        this.user = user;
        this.screening = screening;
        this.currency = currency;
        this.expiresAt = expiresAt;
    }

    void addSeat(Seat seat, BigDecimal price) {
        seats.add(new BookingSeat(this, seat, price));
        totalAmount = totalAmount.add(price);
    }

    public boolean isOwnedBy(long userId) {
        return user != null && user.getId() == userId;
    }

    /** The hold is over (seats may still be held during the payment grace period). */
    public boolean isHoldExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    void confirm(Instant now) {
        status = BookingStatus.CONFIRMED;
        paidAt = now;
    }

    void cancel(Instant now, String refundId) {
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        this.refundId = refundId;
        seats.forEach(BookingSeat::release);
    }

    void expire() {
        status = BookingStatus.EXPIRED;
        seats.forEach(BookingSeat::release);
    }

    void checkIn(Instant now) {
        checkedInAt = now;
    }
}
