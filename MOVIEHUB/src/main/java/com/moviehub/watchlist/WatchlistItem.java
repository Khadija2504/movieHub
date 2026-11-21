package com.moviehub.watchlist;

import com.moviehub.catalog.MediaType;
import com.moviehub.catalog.TitleLookup.TitleSnapshot;
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

@Entity
@Table(name = "watchlist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchlistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10, updatable = false)
    private MediaType mediaType;

    @Column(name = "tmdb_id", nullable = false, updatable = false)
    private Integer tmdbId;

    @Column(nullable = false)
    private String title;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "release_year")
    private Integer releaseYear;

    public WatchlistItem(User user, TitleSnapshot title) {
        this.user = user;
        this.mediaType = title.mediaType();
        this.tmdbId = title.tmdbId();
        this.title = title.title();
        this.posterPath = title.posterPath();
        this.releaseYear = title.year();
    }
}
