package com.moviehub.booking.payment;

import java.math.BigDecimal;

/** A payment provider, seen from the booking side. Amounts are in major units (12.50 EUR). */
public interface PaymentGateway {

    PaymentProvider provider();

    /**
     * Starts the payment of a booking, or returns the one already started ({@code existingPaymentId}).
     *
     * @return the payment; its {@code id} is null when the provider has nothing to track until confirmation
     */
    Payment start(PaymentOrder order, String existingPaymentId);

    /** Current state of a booking's payment. */
    Payment check(PaymentOrder order, String paymentId);

    /** Abandons a payment that did not succeed (best effort). */
    void cancel(String paymentId);

    /** Refunds a successful payment in full. @return the refund id */
    String refund(String paymentId);

    /** What is being paid. */
    record PaymentOrder(long bookingId, String reference, BigDecimal amount, String currency, String description) {
    }

    enum State {
        /** Waiting for the customer (card details, 3-D Secure, ...). */
        OPEN,
        /** Submitted, the outcome is not known yet. */
        PROCESSING,
        SUCCEEDED,
        CANCELED
    }

    /**
     * @param clientSecret secret the browser needs to complete the payment (Stripe), or null
     * @param bookingId    booking id stored on the payment by us, or null if unknown
     */
    record Payment(String id, State state, BigDecimal amount, String currency, String clientSecret, Long bookingId) {
    }
}
