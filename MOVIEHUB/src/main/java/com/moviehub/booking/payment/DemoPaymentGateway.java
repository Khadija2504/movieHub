package com.moviehub.booking.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Development payments: no money moves. The "Pay" button of the demo checkout simply confirms the order,
 * so {@link #check} always reports success, and saved-card renewals always succeed. Only used when Stripe is
 * not configured.
 */
@Component
public class DemoPaymentGateway implements PaymentGateway {

    /** The "saved card" of demo subscriptions. */
    static final String DEMO_CARD = "demo_card";

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.DEMO;
    }

    @Override
    public Payment start(PaymentOrder order, String existingPaymentId) {
        return new Payment(null, State.OPEN, order.amount(), order.currency(), null, order.kind(), order.orderId(),
                null);
    }

    @Override
    public Payment check(PaymentOrder order, String paymentId) {
        String id = paymentId != null ? paymentId : newId("demo_");
        return new Payment(id, State.SUCCEEDED, order.amount(), order.currency(), null, order.kind(), order.orderId(),
                DEMO_CARD);
    }

    @Override
    public void cancel(String paymentId) {
        // Nothing to cancel.
    }

    @Override
    public String refund(String paymentId) {
        return "demo_refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public String customer(long userId, String existingCustomerId) {
        return null;
    }

    @Override
    public Payment chargeSaved(PaymentOrder order, String paymentMethodId) {
        return new Payment(newId("demo_"), State.SUCCEEDED, order.amount(), order.currency(), null, order.kind(),
                order.orderId(), paymentMethodId);
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
