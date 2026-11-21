package com.moviehub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param dir root directory for uploaded files (avatars, ...) */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String dir) {
}
