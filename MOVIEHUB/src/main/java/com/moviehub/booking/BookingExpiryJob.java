package com.moviehub.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Releases the seats of holds that were not paid in time. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bookings.expiry-job", havingValue = "true", matchIfMissing = true)
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);
    private static final int BATCH = 100;

    private final BookingService bookingService;

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT30S")
    public void releaseExpiredHolds() {
        for (Long id : bookingService.staleHoldIds(BATCH)) {
            try {
                bookingService.expireIfStale(id);
            } catch (RuntimeException e) {
                // One failure (e.g. Stripe unreachable) must not block the others; retried next run.
                log.warn("Could not expire booking {}: {}", id, e.getMessage());
            }
        }
    }
}
