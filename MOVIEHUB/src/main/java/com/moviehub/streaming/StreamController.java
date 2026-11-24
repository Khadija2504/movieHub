package com.moviehub.streaming;

import java.net.URI;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.security.UserPrincipal;
import com.moviehub.streaming.StreamService.PlayResult;
import com.moviehub.streaming.StreamingDtos.AccessResponse;
import com.moviehub.streaming.StreamingDtos.ProgressRequest;
import com.moviehub.streaming.StreamingDtos.ProgressResponse;
import com.moviehub.streaming.StreamingDtos.StreamSession;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Watching a movie. {@code tmdbId} is the TMDB id, as in /movies/{id}. */
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Tag(name = "Streaming")
public class StreamController {

    private static final MediaType M3U8 = MediaType.parseMediaType("application/vnd.apple.mpegurl");

    private final StreamService service;

    @GetMapping("/{tmdbId}/access")
    @Operation(summary = "What I can do with a movie: watch (and how), rent, buy")
    public AccessResponse access(@AuthenticationPrincipal UserPrincipal user, @PathVariable int tmdbId) {
        return service.access(user.id(), tmdbId);
    }

    @GetMapping("/{tmdbId}")
    @Operation(summary = "Start watching: short-lived signed video URLs (403 without a purchase, rental or plan)")
    public StreamSession session(@AuthenticationPrincipal UserPrincipal user, @PathVariable int tmdbId) {
        return service.session(user.id(), tmdbId);
    }

    @PutMapping("/{tmdbId}/progress")
    @Operation(summary = "Save where I stopped (the player calls this every few seconds)")
    public ProgressResponse saveProgress(@AuthenticationPrincipal UserPrincipal user, @PathVariable int tmdbId,
            @Valid @RequestBody ProgressRequest request) {
        return service.saveProgress(user.id(), tmdbId, request.position(), request.duration());
    }

    /**
     * Video files. Public: the signed token in the path is the authorisation (a video element cannot send the
     * Authorization header). Byte ranges are supported for MP4 seeking.
     */
    @GetMapping("/play/{token}/{*path}")
    @SecurityRequirements
    @Operation(summary = "Serve a video file of a play URL")
    public ResponseEntity<?> play(@PathVariable String token, @PathVariable String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        PlayResult result = service.play(token, relative);
        if (result instanceof PlayResult.Redirect r) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(r.url()))
                    .cacheControl(CacheControl.noStore()).build();
        }
        if (result instanceof PlayResult.Playlist p) {
            return ResponseEntity.ok().contentType(M3U8).cacheControl(CacheControl.noStore()).body(p.content());
        }
        PlayResult.File f = (PlayResult.File) result;
        boolean playlist = f.path().toString().endsWith(".m3u8");
        Resource body = new FileSystemResource(f.path());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(playlist ? CacheControl.noStore() : CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(body);
    }
}
