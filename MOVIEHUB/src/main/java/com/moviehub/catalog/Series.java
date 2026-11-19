package com.moviehub.catalog;

import java.time.LocalDate;

import com.moviehub.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A TV series imported into the MovieHub catalog. Series are streamed with a subscription (no purchase). */
@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Series extends BaseEntity {

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

    @Column(name = "first_air_date")
    private LocalDate firstAirDate;

    @Column(name = "number_of_seasons")
    private Integer numberOfSeasons;

    @Column(name = "vote_average")
    private Double voteAverage;

    @Column(nullable = false)
    private boolean streamable;

    @Column(nullable = false)
    private boolean active = true;

    public Series(int tmdbId) {
        this.tmdbId = tmdbId;
    }
}
