package com.moviehub.booking.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moviehub.booking.BookingService;
import com.moviehub.store.StoreService;

import io.swagger.v3.oas.annotations.Hidden;
import tools.jackson.databind.ObjectMapper;

/**
 * Stripe webhook (https://docs.stripe.com/webhooks): confirms bookings and store orders whose payment succeeded
 * even when the customer closed the checkout page. Disabled unless STRIPE_WEBHOOK_SECRET is set. Public, but every call
 * must carry a valid Stripe signature.
 */
@Hidden
@RestController
@RequestMapping("/api/payments/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final PaymentProperties props;
    private final BookingService bookingService;
    private final StoreService storeService;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public StripeWebhookController(PaymentProperties props, BookingService bookingService, StoreService storeService,
            ObjectMapper objectMapper) {
        this.props = props;
        this.bookingService = bookingService;
        this.storeService = storeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        String secret = props.stripe().webhookSecret();
        if (!StringUtils.hasText(secret)) {
            return ResponseEntity.notFound().build();
        }
        if (!validSignature(payload, signature, secret, clock.millis() / 1000)) {
            log.warn("Rejected a Stripe webhook with an invalid signature");
            return ResponseEntity.badRequest().build();
        }
        Event event = objectMapper.readValue(payload, Event.class);
        if ("payment_intent.succeeded".equals(event.type()) && event.data() != null
                && event.data().object() != null) {
            StripeObject intent = event.data().object();
            String kind = intent.metadata() == null ? null : intent.metadata().get("kind");
            if (PaymentGateway.PaymentOrder.STORE.equals(kind)) {
                storeService.onPaymentSucceeded(intent.id());
            } else {
                bookingService.onPaymentSucceeded(intent.id());
            }
        }
        return ResponseEntity.ok().build();
    }

    /** Header format: {@code t=<unix seconds>,v1=<hex hmac-sha256 of "t.payload">[,v1=...]}. */
    static boolean validSignature(String payload, String header, String secret, long nowSeconds) {
        if (header == null) {
            return false;
        }
        Long timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.strip().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty() || Math.abs(nowSeconds - timestamp) > TOLERANCE.toSeconds()) {
            return false;
        }
        byte[] expected = HexFormat.of().formatHex(hmac(secret, timestamp + "." + payload))
                .getBytes(StandardCharsets.US_ASCII);
        return signatures.stream()
                .anyMatch(sig -> MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] hmac(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Event(String id, String type, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(StripeObject object) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StripeObject(String id, java.util.Map<String, String> metadata) {
    }
}
