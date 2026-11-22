package com.moviehub.cinema;

import java.math.BigDecimal;
import java.time.Instant;

import com.moviehub.catalog.Movie;
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
import lombok.Setter;

@Entity
@Table(name = "screenings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screening extends BaseEntity {

    public enum Status {
        SCHEDULED,
        CANCELLED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, updatable = false)
    private Movie movie;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    /** Start + runtime + cleaning time: the room is busy until then. */
    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScreeningFormat format;

    /** Audio language, ISO 639-1 upper case (EN, FR, ...). */
    @Column(nullable = false, length = 10)
    private String language;

    /** Subtitle language, or null when none. */
    @Column(length = 10)
    private String subtitles;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal price;

    @Column(name = "vip_price", nullable = false, precision = 6, scale = 2)
    private BigDecimal vipPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    public Screening(Room room, Movie movie, Instant startsAt, Instant endsAt) {
        this.room = room;
        this.movie = movie;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    /** Tickets can be booked until the screening starts. */
    public boolean isBookable(Instant now) {
        return status == Status.SCHEDULED && startsAt.isAfter(now) && room.isActive() && room.getCinema().isActive();
    }
}
