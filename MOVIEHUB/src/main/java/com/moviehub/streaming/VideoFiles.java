package com.moviehub.streaming;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.moviehub.catalog.VideoSource;
import com.moviehub.common.exception.ExternalServiceException;

/**
 * Reads video files: {@code local:} files under the videos directory, and external HLS master playlists
 * (fetched, cached for a few minutes, so they can be filtered before reaching the player).
 */
@Component
public class VideoFiles {

    private static final Logger log = LoggerFactory.getLogger(VideoFiles.class);
    private static final Duration PLAYLIST_CACHE = Duration.ofMinutes(5);
    private static final int MAX_PLAYLIST_SIZE = 512 * 1024;
    /** File types a local HLS stream may serve. */
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "m3u8", "application/vnd.apple.mpegurl",
            "ts", "video/mp2t",
            "m4s", "video/iso.segment",
            "mp4", "video/mp4",
            "m4a", "audio/mp4",
            "aac", "audio/aac",
            "vtt", "text/vtt");

    private final Path root;
    private final RestClient http;
    private final Clock clock = Clock.systemUTC();
    private final Map<String, Cached> playlists = new ConcurrentHashMap<>();

    public VideoFiles(StreamingProperties props, RestClient.Builder builder) {
        this.root = Path.of(props.videosDir()).toAbsolutePath().normalize();
        HttpClient.Builder client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (StringUtils.hasText(props.proxy())) {
            client.proxy(ProxySelector.of(parseProxy(props.proxy())));
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client.build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.clone().requestFactory(factory).build();
        log.info("Streaming: local videos in {}", root);
    }

    /** Folder of a local source ("local:bbb/master.m3u8" -> videos/bbb). */
    public Path localFile(VideoSource source) {
        return safeResolve(root, source.getUrl().substring(VideoSource.LOCAL_PREFIX.length()))
                .orElseThrow(() -> new IllegalStateException("Invalid local video path: " + source.getUrl()));
    }

    /**
     * A file of a local HLS stream, relative to its master playlist.
     *
     * @return empty when the path leaves the stream's folder, has an unknown type, or does not exist
     */
    public Optional<Path> localStreamFile(VideoSource source, String relativePath) {
        Path folder = localFile(source).getParent();
        return safeResolve(folder, relativePath).filter(p -> contentType(p) != null).filter(Files::isRegularFile);
    }

    public String readLocal(Path file) {
        try {
            if (Files.size(file) > MAX_PLAYLIST_SIZE) {
                throw new IllegalStateException("Playlist too large: " + file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    /** An external playlist (cached a few minutes). */
    public String readRemote(String url) {
        Instant now = clock.instant();
        Cached cached = playlists.get(url);
        if (cached != null && cached.until().isAfter(now)) {
            return cached.content();
        }
        String content;
        try {
            content = http.get().uri(URI.create(url)).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw ExternalServiceException.failed("The video host could not be reached", e);
        }
        if (content == null || content.length() > MAX_PLAYLIST_SIZE || !content.startsWith("#EXTM3U")) {
            throw ExternalServiceException.failed("The video host returned an invalid playlist", null);
        }
        if (playlists.size() > 500) {
            playlists.clear();
        }
        playlists.put(url, new Cached(content, now.plus(PLAYLIST_CACHE)));
        return content;
    }

    /** Content type of a file a stream may serve, or null if that type is not allowed. */
    public static String contentType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : CONTENT_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Resolves a relative path inside a folder, refusing anything that escapes it. */
    static Optional<Path> safeResolve(Path folder, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("\\")
                || relativePath.contains("..") || relativePath.startsWith("/")) {
            return Optional.empty();
        }
        try {
            Path resolved = folder.resolve(relativePath).normalize();
            return resolved.startsWith(folder) ? Optional.of(resolved) : Optional.empty();
        } catch (java.nio.file.InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static InetSocketAddress parseProxy(String value) {
        String v = value.strip();
        URI uri = URI.create(v.contains("://") ? v : "http://" + v);
        if (uri.getHost() == null || uri.getPort() < 0) {
            throw new IllegalStateException("app.streaming.proxy must look like http://host:port, got: " + value);
        }
        return InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
    }

    private record Cached(String content, Instant until) {
    }
}
