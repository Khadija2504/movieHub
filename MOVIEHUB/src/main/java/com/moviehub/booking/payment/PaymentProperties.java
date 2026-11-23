package com.moviehub.booking.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * {@code app.payments.*}. Without a Stripe secret key, payments use the built-in demo provider (no real charge).
 *
 * @param currency ISO 4217 code of all prices (EUR)
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(String currency, Stripe stripe) {

    /**
     * @param secretKey      sk_test_... (test mode) or sk_live_...
     * @param publishableKey pk_test_..., sent to the browser for Stripe.js
     * @param webhookSecret  whsec_..., optional: verifies POST /api/payments/stripe/webhook
     * @param apiUrl         https://api.stripe.com (overridable for tests)
     */
    public record Stripe(String secretKey, String publishableKey, String webhookSecret, String apiUrl) {

        public boolean configured() {
            return StringUtils.hasText(secretKey) && StringUtils.hasText(publishableKey);
        }
    }
}
