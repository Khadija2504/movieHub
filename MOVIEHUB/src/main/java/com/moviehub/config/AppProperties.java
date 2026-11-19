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

    /**
     * @param apiKey either a v3 "API Key" (sent as ?api_key=) or a v4 "API Read Access Token" (sent as Bearer header)
     */
    public record Tmdb(String apiKey, String baseUrl, String imageBaseUrl, String language,
            Duration connectTimeout, Duration readTimeout) {
    }

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Admin(String email, String password) {
    }
}
