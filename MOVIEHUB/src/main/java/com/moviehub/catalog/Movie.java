package com.moviehub.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.moviehub.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A movie imported into the MovieHub catalog, with its MovieHub-specific offer (streaming, prices).
 * Its video files are {@link VideoSource}s.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie extends BaseEntity {

    @Column(name = "tmdb_id", nullable = false, unique = true, updatable = false)
    private Integer tmdbId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "backdrop_path")
    private String backdropPath;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    private Integer runtime;

    @Column(name = "vote_average")
    private Double voteAverage;

    @Column(nullable = false)
    private boolean streamable;

    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "rental_price", precision = 10, scale = 2)
    private BigDecimal rentalPrice;

    /** false = buy or rent only, even for subscribers. */
    @Column(name = "in_subscription", nullable = false)
    private boolean inSubscription = true;

    /** Licence / credit line shown in the player. */
    @Column(name = "video_credit")
    private String videoCredit;

    @Column(nullable = false)
    private boolean active = true;

    public Movie(int tmdbId) {
        this.tmdbId = tmdbId;
    }
}
