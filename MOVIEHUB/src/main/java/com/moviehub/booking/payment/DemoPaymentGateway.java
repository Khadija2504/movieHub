package com.moviehub.booking.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Development payments: no money moves. The "Pay" button of the demo checkout simply confirms the booking,
 * so {@link #check} always reports success. Only used when Stripe is not configured.
 */
@Component
public class DemoPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.DEMO;
    }

    @Override
    public Payment start(PaymentOrder order, String existingPaymentId) {
        return new Payment(null, State.OPEN, order.amount(), order.currency(), null, order.bookingId());
    }

    @Override
    public Payment check(PaymentOrder order, String paymentId) {
        String id = paymentId != null ? paymentId : "demo_" + UUID.randomUUID().toString().replace("-", "");
        return new Payment(id, State.SUCCEEDED, order.amount(), order.currency(), null, order.bookingId());
    }

    @Override
    public void cancel(String paymentId) {
        // Nothing to cancel.
    }

    @Override
    public String refund(String paymentId) {
        return "demo_refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
