package com.moviehub.booking.payment;

import java.math.BigDecimal;

/**
 * A payment provider, seen from MovieHub: cinema bookings and store orders (purchases, rentals, subscriptions).
 * Amounts are in major units (12.50 EUR).
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /**
     * Starts the payment of an order, or returns the one already started ({@code existingPaymentId}).
     *
     * @return the payment; its {@code id} is null when the provider has nothing to track until confirmation
     */
    Payment start(PaymentOrder order, String existingPaymentId);

    /** Current state of an order's payment. */
    Payment check(PaymentOrder order, String paymentId);

    /** Abandons a payment that did not succeed (best effort). */
    void cancel(String paymentId);

    /** Refunds a successful payment in full. @return the refund id */
    String refund(String paymentId);

    /**
     * The customer record that keeps a saved card for later charges (subscription renewals).
     *
     * @return the existing id, a new one, or null when the provider needs none
     */
    String customer(long userId, String existingCustomerId);

    /**
     * Charges a saved card without the customer being there (subscription renewal). A declined card is reported
     * as {@link State#CANCELED}, not as an exception.
     */
    Payment chargeSaved(PaymentOrder order, String paymentMethodId);

    /**
     * What is being paid.
     *
     * @param kind       {@link #BOOKING} or {@link #STORE}: stored on the payment, so a webhook knows where it belongs
     * @param customerId provider customer to save the card on (subscriptions), or null
     * @param saveCard   keep the card for later charges without the customer
     */
    record PaymentOrder(String kind, long orderId, String reference, BigDecimal amount, String currency,
            String description, String customerId, boolean saveCard) {

        public static final String BOOKING = "booking";
        public static final String STORE = "order";

        /** A cinema booking (one-off payment). */
        public PaymentOrder(long bookingId, String reference, BigDecimal amount, String currency, String description) {
            this(BOOKING, bookingId, reference, amount, currency, description, null, false);
        }
    }

    enum State {
        /** Waiting for the customer (card details, 3-D Secure, ...). */
        OPEN,
        /** Submitted, the outcome is not known yet. */
        PROCESSING,
        SUCCEEDED,
        /** Abandoned or declined. */
        CANCELED
    }

    /**
     * @param clientSecret    secret the browser needs to complete the payment (Stripe), or null
     * @param kind            {@link PaymentOrder#kind()} stored on the payment by us, or null if unknown
     * @param orderId         booking / order id stored on the payment by us, or null if unknown
     * @param paymentMethodId card used (Stripe pm_...), to charge it again later; null if unknown
     */
    record Payment(String id, State state, BigDecimal amount, String currency, String clientSecret, String kind,
            Long orderId, String paymentMethodId) {

        /** Whether this payment was made for that order (missing metadata counts as a match). */
        public boolean belongsTo(String expectedKind, long expectedId) {
            boolean kindMatches = kind == null || kind.equals(expectedKind);
            return kindMatches && (orderId == null || orderId == expectedId);
        }
    }
}
