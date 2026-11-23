package com.moviehub.booking.payment;

public enum PaymentProvider {
    /** Stripe (test or live mode depending on the keys). */
    STRIPE,
    /** Built-in fake payment for development, used when no Stripe key is configured. */
    DEMO
}
