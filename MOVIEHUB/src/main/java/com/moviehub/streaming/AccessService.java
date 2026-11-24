package com.moviehub.streaming;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviehub.catalog.Movie;
import com.moviehub.library.LibraryItem;
import com.moviehub.library.LibraryRepository;
import com.moviehub.subscription.UserSubscription;
import com.moviehub.subscription.UserSubscriptionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Who may watch what. A movie can be watched when it is streamable and the user bought it, rents it, or holds a
 * subscription that includes it. Purchases and rentals play in the best quality; subscriptions up to the plan's
 * quality.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final LibraryRepository libraryRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final Clock clock = Clock.systemUTC();

    public enum Via {
        PURCHASE,
        RENTAL,
        SUBSCRIPTION
    }

    /**
     * @param via          null = no access
     * @param rentalEndsAt end of the rental (RENTAL only)
     * @param maxQuality   highest picture height allowed; null = no limit
     */
    public record Access(Via via, Instant rentalEndsAt, Integer maxQuality) {

        public static final Access NONE = new Access(null, null, null);

        public boolean granted() {
            return via != null;
        }
    }

    /** Everything a user has, loaded once to check many movies (library, history, ...). */
    public record UserAccess(Map<Long, LibraryItem> library, UserSubscription subscription, Instant now) {

        public Access check(Movie movie) {
            if (!movie.isStreamable()) {
                return Access.NONE;
            }
            LibraryItem item = library.get(movie.getId());
            if (item != null && item.isOwned()) {
                return new Access(Via.PURCHASE, null, null);
            }
            if (item != null && item.isRentalActive(now)) {
                return new Access(Via.RENTAL, item.getExpiresAt(), null);
            }
            if (movie.isActive() && movie.isInSubscription() && subscribed()) {
                return new Access(Via.SUBSCRIPTION, null, subscription.getPlan().getMaxQuality());
            }
            return Access.NONE;
        }

        /**
         * A running subscription. One whose period just ended but renews automatically keeps working until the
         * renewal job has charged it (or ended it).
         */
        public boolean subscribed() {
            return subscription != null && subscription.getStatus() == UserSubscription.Status.ACTIVE
                    && (now.isBefore(subscription.getCurrentPeriodEnd()) || subscription.isAutoRenew());
        }

        public LibraryItem item(Movie movie) {
            return library.get(movie.getId());
        }
    }

    @Transactional(readOnly = true)
    public UserAccess forUser(long userId) {
        Map<Long, LibraryItem> library = libraryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(l -> l.getMovie().getId(), Function.identity()));
        UserSubscription subscription = subscriptionRepository.findActive(userId).orElse(null);
        return new UserAccess(library, subscription, clock.instant());
    }

    @Transactional(readOnly = true)
    public Access check(long userId, Movie movie) {
        return forUser(userId).check(movie);
    }
}
