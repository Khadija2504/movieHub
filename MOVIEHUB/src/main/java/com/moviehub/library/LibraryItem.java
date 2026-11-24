package com.moviehub.library;

import java.time.Duration;
import java.time.Instant;

import com.moviehub.catalog.Movie;
import com.moviehub.common.entity.BaseEntity;
import com.moviehub.store.StoreOrder;
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

/**
 * A movie in a user's library: bought (forever) or rented (until {@code expiresAt}). One row per user and movie:
 * renting again extends it, buying turns a rental into a purchase.
 */
@Entity
@Table(name = "library_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LibraryItem extends BaseEntity {

    public enum Kind {
        PURCHASE,
        RENTAL
    }

    /** How long a rental lasts. */
    public static final Duration RENTAL_TIME = Duration.ofHours(48);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, updatable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private StoreOrder order;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    /** End of a rental; null for a purchase. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    public LibraryItem(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
    }

    public void purchase(StoreOrder order, Instant now) {
        this.kind = Kind.PURCHASE;
        this.order = order;
        this.acquiredAt = now;
        this.expiresAt = null;
    }

    public void rent(StoreOrder order, Instant now) {
        this.kind = Kind.RENTAL;
        this.order = order;
        this.acquiredAt = now;
        this.expiresAt = now.plus(RENTAL_TIME);
    }

    public boolean isOwned() {
        return kind == Kind.PURCHASE;
    }

    public boolean isRentalActive(Instant now) {
        return kind == Kind.RENTAL && expiresAt != null && now.isBefore(expiresAt);
    }

    /** Bought, or rented and not over. */
    public boolean grantsAccess(Instant now) {
        return isOwned() || isRentalActive(now);
    }
}
