package com.moviehub.booking;

import java.math.BigDecimal;

import com.moviehub.cinema.Screening;
import com.moviehub.cinema.Seat;
import com.moviehub.cinema.SeatType;
import com.moviehub.common.entity.BaseEntity;

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

/** One seat of a booking, with the price it was sold at. */
@Entity
@Table(name = "booking_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false, updatable = false)
    private Seat seat;

    @Column(name = "seat_label", nullable = false, length = 5, updatable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20, updatable = false)
    private SeatType type;

    @Column(nullable = false, precision = 6, scale = 2, updatable = false)
    private BigDecimal price;

    /** False once the seat has been released (booking cancelled or expired). */
    @Column(nullable = false)
    private boolean active = true;

    BookingSeat(Booking booking, Seat seat, BigDecimal price) {
        this.booking = booking;
        this.screening = booking.getScreening();
        this.seat = seat;
        this.label = seat.label();
        this.type = seat.getType();
        this.price = price;
    }

    void release() {
        this.active = false;
    }
}
