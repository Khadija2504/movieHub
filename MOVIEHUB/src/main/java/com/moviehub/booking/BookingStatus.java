package com.moviehub.booking;

public enum BookingStatus {
    /** Seats are held while the customer pays. */
    PENDING,
    /** Paid: the ticket is valid. */
    CONFIRMED,
    /** Cancelled by the customer or because the screening was cancelled (refunded when paid). */
    CANCELLED,
    /** The hold ran out before payment. */
    EXPIRED
}
