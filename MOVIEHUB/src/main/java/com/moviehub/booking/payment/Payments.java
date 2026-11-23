package com.moviehub.booking.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moviehub.common.exception.ExternalServiceException;

/** Picks the payment provider: Stripe when its keys are set, the demo provider otherwise. */
@Component
public class Payments {

    private static final Logger log = LoggerFactory.getLogger(Payments.class);

    private final StripePaymentGateway stripe;
    private final DemoPaymentGateway demo;
    private final PaymentProperties props;

    public Payments(StripePaymentGateway stripe, DemoPaymentGateway demo, PaymentProperties props) {
        this.stripe = stripe;
        this.demo = demo;
        this.props = props;
        if (stripe.configured()) {
            log.info("Payments: Stripe ({} mode)", props.stripe().secretKey().startsWith("sk_live") ? "LIVE" : "test");
        } else {
            log.warn("Payments: STRIPE_SECRET_KEY / STRIPE_PUBLISHABLE_KEY not set, using DEMO payments (no real charge)");
        }
    }

    /** Provider used for new payments. */
    public PaymentGateway current() {
        return stripe.configured() ? stripe : demo;
    }

    /** Provider of an existing payment. */
    public PaymentGateway of(PaymentProvider provider) {
        return switch (provider) {
            case STRIPE -> {
                if (!stripe.configured()) {
                    throw ExternalServiceException.unavailable("Card payments are not configured");
                }
                yield stripe;
            }
            case DEMO -> demo;
        };
    }

    /** Key the browser needs for Stripe.js, or null with the demo provider. */
    public String publishableKey() {
        return stripe.configured() ? stripe.publishableKey() : null;
    }

    public String currency() {
        return props.currency();
    }
}
