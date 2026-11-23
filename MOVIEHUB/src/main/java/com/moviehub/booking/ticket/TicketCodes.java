package com.moviehub.booking.ticket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Content of the ticket QR code: {@code MH1.<reference>.<signature>}. The HMAC signature lets the cinema check
 * that a code was issued by MovieHub without trusting what the customer shows.
 */
@Component
public class TicketCodes {

    private static final Logger log = LoggerFactory.getLogger(TicketCodes.class);
    private static final String PREFIX = "MH1";
    private static final int SIGNATURE_LENGTH = 22;

    private final byte[] key;

    public TicketCodes(@Value("${app.tickets.secret:}") String ticketSecret, @Value("${app.jwt.secret:}") String jwtSecret) {
        this.key = buildKey(ticketSecret, jwtSecret);
    }

    public String issue(String reference) {
        return PREFIX + "." + reference + "." + sign(reference);
    }

    /** @return the booking reference when the code is genuine */
    public Optional<String> verify(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String[] parts = code.strip().split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0]) || parts[1].isEmpty()) {
            return Optional.empty();
        }
        boolean valid = MessageDigest.isEqual(sign(parts[1]).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII));
        return valid ? Optional.of(parts[1]) : Optional.empty();
    }

    private String sign(String reference) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((PREFIX + ":" + reference).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, SIGNATURE_LENGTH);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static byte[] buildKey(String ticketSecret, String jwtSecret) {
        if (StringUtils.hasText(ticketSecret)) {
            return ticketSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (StringUtils.hasText(jwtSecret)) {
            // Derived, so a ticket code never doubles as a JWT signature.
            return ("tickets:" + jwtSecret).getBytes(StandardCharsets.UTF_8);
        }
        log.warn("TICKET_SECRET and JWT_SECRET are not set: ticket QR codes will be invalid after a restart.");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }
}
