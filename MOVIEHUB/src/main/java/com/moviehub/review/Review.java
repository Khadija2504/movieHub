package com.moviehub.review;

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
import lombok.Setter;

/** One rating (1-10) + optional comment per user and title. */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    public enum Status {
        VISIBLE,
        HIDDEN
    }

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

    @Column(nullable = false)
    private Short rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.VISIBLE;

    public Review(User user, TitleSnapshot title) {
        this.user = user;
        this.mediaType = title.mediaType();
        this.tmdbId = title.tmdbId();
        this.title = title.title();
        this.posterPath = title.posterPath();
    }
}
