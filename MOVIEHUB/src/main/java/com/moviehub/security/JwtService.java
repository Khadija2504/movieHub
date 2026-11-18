package com.moviehub.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.moviehub.config.AppProperties;
import com.moviehub.user.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/** Issues and validates short-lived access tokens (HS256). */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "moviehub";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    @Autowired
    public JwtService(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    JwtService(AppProperties props, Clock clock) {
        this.key = buildKey(props.jwt().secret());
        this.accessTokenTtl = props.jwt().accessTokenTtl();
        this.clock = clock;
    }

    public String generateAccessToken(UserPrincipal user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.id().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** @return the principal if the token is valid (signature, issuer, expiry), empty otherwise. */
    public Optional<UserPrincipal> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UserPrincipal.fromToken(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    private static SecretKey buildKey(String base64Secret) {
        if (!StringUtils.hasText(base64Secret)) {
            log.warn("JWT_SECRET is not set: using a random key. Tokens will be invalid after a restart.");
            return Jwts.SIG.HS256.key().build();
        }
        byte[] bytes = Decoders.BASE64.decode(base64Secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be a base64 value of at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
