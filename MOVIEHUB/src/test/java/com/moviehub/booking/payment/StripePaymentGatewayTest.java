package com.moviehub.booking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.moviehub.booking.payment.PaymentGateway.PaymentOrder;
import com.moviehub.booking.payment.PaymentGateway.State;
import com.moviehub.common.exception.ExternalServiceException;

/** The Stripe REST calls, against a mock server. */
class StripePaymentGatewayTest {

    private static final String API = "https://stripe.test";
    private static final PaymentOrder ORDER = new PaymentOrder(42, "MH-ABC234", new BigDecimal("24.50"), "EUR",
            "MovieHub MH-ABC234");

    private MockRestServiceServer server;
    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new StripePaymentGateway(new PaymentProperties.Stripe("sk_test_123", "pk_test_123", null, API),
                builder);
    }

    private static String intent(String status) {
        return """
                {"id":"pi_1","object":"payment_intent","status":"%s","amount":2450,"currency":"eur",
                 "client_secret":"pi_1_secret_x","metadata":{"booking_id":"42","reference":"MH-ABC234"}}"""
                .formatted(status);
    }

    @Test
    void createsAPaymentIntentInCents() {
        server.expect(requestTo(API + "/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_123"))
                .andExpect(header("Idempotency-Key", "moviehub-booking-MH-ABC234"))
                .andExpect(content().formDataContains(java.util.Map.of("amount", "2450", "currency", "eur",
                        "metadata[booking_id]", "42", "automatic_payment_methods[enabled]", "true")))
                .andRespond(withSuccess(intent("requires_payment_method"), MediaType.APPLICATION_JSON));

        var payment = gateway.start(ORDER, null);

        assertThat(payment.id()).isEqualTo("pi_1");
        assertThat(payment.state()).isEqualTo(State.OPEN);
        assertThat(payment.clientSecret()).isEqualTo("pi_1_secret_x");
        assertThat(payment.amount()).isEqualByComparingTo("24.50");
        assertThat(payment.currency()).isEqualTo("EUR");
        assertThat(payment.bookingId()).isEqualTo(42L);
        server.verify();
    }

    @Test
    void reusesAnExistingIntent() {
        server.expect(requestTo(API + "/v1/payment_intents/pi_1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(intent("succeeded"), MediaType.APPLICATION_JSON));

        assertThat(gateway.start(ORDER, "pi_1").state()).isEqualTo(State.SUCCEEDED);
        server.verify();
    }

    @Test
    void refundsAndReportsErrors() {
        server.expect(requestTo(API + "/v1/refunds"))
                .andExpect(content().formDataContains(java.util.Map.of("payment_intent", "pi_1")))
                .andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/v1/refunds"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Charge already refunded\"}}"));

        assertThat(gateway.refund("pi_1")).isEqualTo("re_1");
        assertThatThrownBy(() -> gateway.refund("pi_1")).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void notConfiguredMeansUnavailable() {
        var unconfigured = new StripePaymentGateway(new PaymentProperties.Stripe("", "", null, API),
                RestClient.builder());
        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.check(ORDER, "pi_1"))
                .isInstanceOfSatisfying(ExternalServiceException.class, e -> assertThat(e.isUnavailable()).isTrue());
    }

    @Test
    void verifiesWebhookSignatures() {
        String payload = "{\"type\":\"payment_intent.succeeded\"}";
        long now = 1_800_000_000L;
        String sig = HexFormat.of().formatHex(StripeWebhookController.hmac("whsec_test", now + "." + payload));

        assertThat(StripeWebhookController.validSignature(payload, "t=" + now + ",v1=" + sig, "whsec_test", now))
                .isTrue();
        assertThat(StripeWebhookController.validSignature(payload, "t=" + now + ",v1=" + sig, "whsec_other", now))
                .isFalse();
        assertThat(StripeWebhookController.validSignature(payload + " ", "t=" + now + ",v1=" + sig, "whsec_test",
                now)).isFalse();
        assertThat(StripeWebhookController.validSignature(payload, "t=" + now + ",v1=" + sig, "whsec_test",
                now + 600)).isFalse();
        assertThat(StripeWebhookController.validSignature(payload, null, "whsec_test", now)).isFalse();
        assertThat(new String(sig.getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII)).hasSize(64);
    }
}
