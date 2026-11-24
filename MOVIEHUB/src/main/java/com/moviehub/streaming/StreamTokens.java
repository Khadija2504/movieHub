package com.moviehub.streaming;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
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
 * Short-lived signed play URLs. A video element cannot send the Authorization header, so the token in the URL
 * path is the proof of access: {@code base64url(v1|user|source|maxQuality|expiry).signature}.
 */
@Component
public class StreamTokens {

    private static final Logger log = LoggerFactory.getLogger(StreamTokens.class);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final int SIGNATURE_LENGTH = 32;

    private final byte[] key;

    public StreamTokens(StreamingProperties props, @Value("${app.jwt.secret:}") String jwtSecret) {
        this.key = buildKey(props.secret(), jwtSecret);
    }

    /**
     * What a token allows: playing one video source of one movie until {@code expiresAt}.
     *
     * @param maxQuality highest picture height allowed; 0 = no limit
     */
    public record Grant(long userId, long sourceId, int maxQuality, Instant expiresAt) {
    }

    public String issue(Grant grant) {
        String payload = "v1|" + grant.userId() + "|" + grant.sourceId() + "|" + grant.maxQuality() + "|"
                + grant.expiresAt().getEpochSecond();
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    /** @return the grant if the token is genuine (expiry is checked by the caller) */
    public Optional<Grant> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String encoded = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!MessageDigest.isEqual(sign(encoded).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            String[] parts = new String(DECODER.decode(encoded), StandardCharsets.UTF_8).split("\\|");
            if (parts.length != 5 || !"v1".equals(parts[0])) {
                return Optional.empty();
            }
            return Optional.of(new Grant(Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3]), Instant.ofEpochSecond(Long.parseLong(parts[4]))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, SIGNATURE_LENGTH);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static byte[] buildKey(String secret, String jwtSecret) {
        if (StringUtils.hasText(secret)) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
        if (StringUtils.hasText(jwtSecret)) {
            // Derived, so a play token never doubles as a JWT or ticket signature.
            return ("stream:" + jwtSecret).getBytes(StandardCharsets.UTF_8);
        }
        log.warn("STREAM_SECRET and JWT_SECRET are not set: play URLs stop working after a restart.");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return random;
    }
}
