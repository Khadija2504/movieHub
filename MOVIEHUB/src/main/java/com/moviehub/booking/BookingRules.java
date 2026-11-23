package com.moviehub.booking;

import java.time.Duration;

/** Business constants of cinema bookings. */
public final class BookingRules {

    /** How long seats stay held while the customer pays. */
    public static final Duration HOLD_TIME = Duration.ofMinutes(10);

    /**
     * Extra time given to a booking whose payment has started: a card payment (3-D Secure) can finish a bit
     * after the hold. Seats are only released once the payment is known to have failed or this has passed.
     */
    public static final Duration PAYMENT_GRACE = Duration.ofMinutes(15);

    public static final int MAX_SEATS = 10;

    /** Paid bookings can be cancelled (and refunded) until this long before the screening. */
    public static final Duration CANCELLATION_DEADLINE = Duration.ofHours(2);

    private BookingRules() {
    }
}
