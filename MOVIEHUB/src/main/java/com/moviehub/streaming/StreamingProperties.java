package com.moviehub.streaming;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.streaming.*}.
 *
 * @param secret    signs play URLs; empty = derived from the JWT secret
 * @param urlTtl    how long a play URL works (the player asks for a new one when it runs out)
 * @param videosDir root of {@code local:} videos; relative paths are resolved from the working directory
 * @param proxy     optional HTTP proxy ("http://host:port") used to read external HLS playlists
 */
@ConfigurationProperties(prefix = "app.streaming")
public record StreamingProperties(String secret, Duration urlTtl, String videosDir, String proxy) {

    public StreamingProperties {
        if (urlTtl == null || urlTtl.isNegative() || urlTtl.isZero()) {
            urlTtl = Duration.ofHours(4);
        }
        if (videosDir == null || videosDir.isBlank()) {
            videosDir = "./storage/videos";
        }
    }
}
