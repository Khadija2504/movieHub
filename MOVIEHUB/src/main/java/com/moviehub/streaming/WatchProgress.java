package com.moviehub.streaming;

import java.time.Instant;

import com.moviehub.catalog.Movie;
import com.moviehub.common.entity.BaseEntity;
import com.moviehub.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Where a user stopped watching a movie. One row per user and movie: it is the watch history. */
@Entity
@Table(name = "watch_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchProgress extends BaseEntity {

    /** Watched this far = finished. */
    static final double COMPLETED_RATIO = 0.95;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, updatable = false)
    private Movie movie;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt;

    public WatchProgress(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
    }

    public void update(int position, int duration, Instant now) {
        this.durationSeconds = Math.max(duration, 1);
        this.positionSeconds = Math.min(Math.max(position, 0), durationSeconds);
        // The end credits do not count: 95% watched = finished.
        this.completed = positionSeconds >= durationSeconds * COMPLETED_RATIO;
        this.lastWatchedAt = now;
    }

    /** Where to start next time: from the beginning once finished. */
    public int resumeAt() {
        return completed ? 0 : positionSeconds;
    }
}
