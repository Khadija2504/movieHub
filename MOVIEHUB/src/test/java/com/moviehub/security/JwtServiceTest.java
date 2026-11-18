package com.moviehub.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.moviehub.config.AppProperties;
import com.moviehub.user.Role;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static JwtService serviceAt(Instant instant, String secret) {
        var props = new AppProperties(null, null,
                new AppProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(7)), null);
        return new JwtService(props, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static final UserPrincipal USER = UserPrincipal.fromToken(42L, "jane@example.com", Role.ADMIN);

    @Test
    void roundTripsUserClaims() {
        JwtService jwt = serviceAt(NOW, SECRET);

        UserPrincipal parsed = jwt.parseAccessToken(jwt.generateAccessToken(USER)).orElseThrow();

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("jane@example.com");
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
        assertThat(parsed.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsExpiredToken() {
        String token = serviceAt(NOW, SECRET).generateAccessToken(USER);

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(16)), SECRET).parseAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String otherSecret = Base64.getEncoder().encodeToString("another-secret-that-is-long-enough-123456".getBytes());
        String token = serviceAt(NOW, otherSecret).generateAccessToken(USER);

        assertThat(serviceAt(NOW, SECRET).parseAccessToken(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(serviceAt(NOW, SECRET).parseAccessToken("not-a-jwt")).isEmpty();
    }
}
