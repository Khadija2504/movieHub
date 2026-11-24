package com.moviehub.streaming;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.moviehub.library.LibraryItem;
import com.moviehub.streaming.AccessService.Via;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code tmdbId} is the TMDB id, as used in /movies/{id} and /watch/{id}. */
public final class StreamingDtos {

    private StreamingDtos() {
    }

    /**
     * What the current user can do with a movie (drives the Watch / Rent / Buy buttons).
     *
     * @param streamable     the movie can be watched on MovieHub at all
     * @param via            how the user can watch it now; null = they can't
     * @param subscribed     the user holds a running subscription
     * @param inSubscription subscribers watch it at no extra cost
     * @param resumeAt       seconds to resume from (0 = start)
     */
    public record AccessResponse(
            int tmdbId,
            boolean streamable,
            boolean canWatch,
            Via via,
            Instant rentalEndsAt,
            Integer maxQuality,
            boolean owned,
            boolean subscribed,
            boolean inSubscription,
            BigDecimal purchasePrice,
            BigDecimal rentalPrice,
            int resumeAt,
            Integer progressPercent) {
    }

    public enum SourceType {
        HLS,
        MP4
    }

    /**
     * @param url     play URL (path from the API host, e.g. /api/stream/play/...): short-lived, signed
     * @param quality picture height of an MP4; null for HLS (the player lists the stream's qualities)
     */
    public record StreamSource(String url, SourceType type, Integer quality) {
    }

    /**
     * Everything the player needs.
     *
     * @param expiresAt  when the play URLs stop working (ask for a new session then)
     * @param maxQuality highest picture height of the user's access; null = no limit
     */
    public record StreamSession(
            int tmdbId,
            String title,
            String posterUrl,
            String backdropUrl,
            Via via,
            Instant rentalEndsAt,
            Integer maxQuality,
            List<StreamSource> sources,
            Instant expiresAt,
            int resumeAt,
            String credit) {
    }

    public record ProgressRequest(
            @NotNull @Min(0) @Max(86_400) Integer position,
            @NotNull @Min(1) @Max(86_400) Integer duration) {
    }

    public record ProgressResponse(int position, int duration, boolean completed, int resumeAt) {
    }

    /** A movie of the watch history / "Continue watching". */
    public record WatchedMovie(
            int tmdbId,
            String title,
            String posterUrl,
            String backdropUrl,
            Integer year,
            int position,
            int duration,
            int percent,
            boolean completed,
            Instant lastWatchedAt,
            boolean canWatch) {
    }

    /**
     * @param active purchase, or rental not over yet
     */
    public record LibraryEntry(
            int tmdbId,
            String title,
            String posterUrl,
            String backdropUrl,
            Integer year,
            Integer runtime,
            LibraryItem.Kind kind,
            Instant acquiredAt,
            Instant expiresAt,
            boolean active,
            BigDecimal rentalPrice,
            Integer progressPercent) {
    }
}
