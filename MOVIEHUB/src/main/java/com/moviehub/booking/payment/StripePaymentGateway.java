package com.moviehub.booking.payment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moviehub.common.exception.ExternalServiceException;

/**
 * Stripe PaymentIntents over the REST API (https://docs.stripe.com/api/payment_intents).
 * The browser completes the payment with Stripe.js using the intent's client secret; the backend only
 * creates, checks, cancels and refunds intents with the secret key.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final String DEFAULT_API_URL = "https://api.stripe.com";

    private final PaymentProperties.Stripe config;
    private final RestClient client;

    @Autowired
    public StripePaymentGateway(PaymentProperties props, RestClient.Builder builder) {
        this(props.stripe(), builder.clone().requestFactory(requestFactory()));
    }

    /** @param builder pre-configured transport (tests bind a mock server to it) */
    StripePaymentGateway(PaymentProperties.Stripe config, RestClient.Builder builder) {
        this.config = config;
        String apiUrl = config.apiUrl() == null || config.apiUrl().isBlank() ? DEFAULT_API_URL : config.apiUrl();
        this.client = builder
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.secretKey())
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return requestFactory;
    }

    public boolean configured() {
        return config.configured();
    }

    public String publishableKey() {
        return config.publishableKey();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public Payment start(PaymentOrder order, String existingPaymentId) {
        if (existingPaymentId != null) {
            Payment existing = check(order, existingPaymentId);
            if (existing.state() != State.CANCELED) {
                return existing;
            }
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Long.toString(toMinor(order.amount())));
        form.add("currency", order.currency().toLowerCase(Locale.ROOT));
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("description", order.description());
        addMetadata(form, order);
        if (order.customerId() != null) {
            form.add("customer", order.customerId());
        }
        if (order.saveCard()) {
            // Keeps the card on the customer, for renewals charged without them.
            form.add("setup_future_usage", "off_session");
        }
        // Same order => same intent, even if the request is retried.
        String idempotencyKey = "moviehub-" + order.kind() + "-" + order.reference()
                + (existingPaymentId != null ? "-" + existingPaymentId : "");
        return toPayment(call(() -> client.post().uri("/v1/payment_intents")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(Intent.class)));
    }

    @Override
    public Payment check(PaymentOrder order, String paymentId) {
        return toPayment(call(() -> client.get().uri("/v1/payment_intents/{id}", paymentId)
                .retrieve().body(Intent.class)));
    }

    @Override
    public void cancel(String paymentId) {
        try {
            client.post().uri("/v1/payment_intents/{id}/cancel", paymentId)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(new LinkedMultiValueMap<>())
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            // Already canceled / succeeded / unknown: nothing more to do.
        }
    }

    @Override
    public String refund(String paymentId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentId);
        Refund refund = call(() -> client.post().uri("/v1/refunds")
                .header("Idempotency-Key", "moviehub-refund-" + paymentId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(Refund.class));
        return refund.id();
    }

    @Override
    public String customer(long userId, String existingCustomerId) {
        if (existingCustomerId != null) {
            return existingCustomerId;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("metadata[user_id]", Long.toString(userId));
        Customer customer = call(() -> client.post().uri("/v1/customers")
                .header("Idempotency-Key", "moviehub-customer-" + userId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(Customer.class));
        return customer.id();
    }

    @Override
    public Payment chargeSaved(PaymentOrder order, String paymentMethodId) {
        if (order.customerId() == null || paymentMethodId == null) {
            return declined(order);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Long.toString(toMinor(order.amount())));
        form.add("currency", order.currency().toLowerCase(Locale.ROOT));
        form.add("customer", order.customerId());
        form.add("payment_method", paymentMethodId);
        form.add("off_session", "true");
        form.add("confirm", "true");
        form.add("description", order.description());
        addMetadata(form, order);
        try {
            return toPayment(call(() -> client.post().uri("/v1/payment_intents")
                    .header("Idempotency-Key", "moviehub-" + order.kind() + "-" + order.reference())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(Intent.class)));
        } catch (ExternalServiceException e) {
            if (e.getCause() instanceof RestClientResponseException response
                    && response.getStatusCode().value() == 402) {
                // Card declined or authentication required: the renewal failed.
                return declined(order);
            }
            throw e;
        }
    }

    private static Payment declined(PaymentOrder order) {
        return new Payment(null, State.CANCELED, order.amount(), order.currency(), null, order.kind(),
                order.orderId(), null);
    }

    private static void addMetadata(MultiValueMap<String, String> form, PaymentOrder order) {
        form.add("metadata[kind]", order.kind());
        form.add("metadata[order_id]", Long.toString(order.orderId()));
        form.add("metadata[reference]", order.reference());
    }

    /** Parses a PaymentIntent object (also used for webhook events). */
    Payment toPayment(Intent intent) {
        State state = switch (intent.status()) {
            case "succeeded" -> State.SUCCEEDED;
            case "processing" -> State.PROCESSING;
            case "canceled" -> State.CANCELED;
            default -> State.OPEN; // requires_payment_method, requires_confirmation, requires_action, ...
        };
        Map<String, String> metadata = intent.metadata() == null ? Map.of() : intent.metadata();
        String orderId = metadata.getOrDefault("order_id", metadata.get("booking_id"));
        return new Payment(intent.id(), state, BigDecimal.valueOf(intent.amount(), 2),
                intent.currency() == null ? null : intent.currency().toUpperCase(Locale.ROOT), intent.clientSecret(),
                metadata.get("kind"), orderId == null ? null : Long.valueOf(orderId), intent.paymentMethod());
    }

    static long toMinor(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        if (!configured()) {
            throw ExternalServiceException.unavailable("Card payments are not configured");
        }
        try {
            T result = request.get();
            if (result == null) {
                throw ExternalServiceException.failed("Empty answer from the payment provider", null);
            }
            return result;
        } catch (RestClientResponseException e) {
            throw ExternalServiceException.failed("The payment provider rejected the request (HTTP "
                    + e.getStatusCode().value() + ")", e);
        } catch (ResourceAccessException e) {
            throw ExternalServiceException.failed("The payment provider is unreachable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Intent(String id, String status, long amount, String currency,
            @JsonProperty("client_secret") String clientSecret, Map<String, String> metadata,
            @JsonProperty("payment_method") String paymentMethod) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Customer(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Refund(String id, String status) {
    }
}
