package com.moviehub.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed access to the custom {@code app.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Tmdb tmdb) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Tmdb(String apiKey, String baseUrl, String imageBaseUrl) {
    }
}
