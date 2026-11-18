package com.moviehub.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed access to the custom {@code app.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Tmdb tmdb, Jwt jwt, Admin admin) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Tmdb(String apiKey, String baseUrl, String imageBaseUrl) {
    }

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Admin(String email, String password) {
    }
}
