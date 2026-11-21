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
     * @param proxy  optional HTTP proxy for outgoing TMDB calls ("http://host:port" or "host:port"), blank for none
     */
    public record Tmdb(String apiKey, String baseUrl, String imageBaseUrl, String language,
            Duration connectTimeout, Duration readTimeout, String proxy) {
    }

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Admin(String email, String password) {
    }
}
