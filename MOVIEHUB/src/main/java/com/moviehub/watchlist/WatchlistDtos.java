package com.moviehub.watchlist;

import java.time.Instant;

import com.moviehub.catalog.MediaType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class WatchlistDtos {

    private WatchlistDtos() {
    }

    /** Identifies a title. {@code tmdbId} is the TMDB id, as used in /movies/{id} and /series/{id}. */
    public record TitleKey(MediaType mediaType, Integer tmdbId) {
    }

    public record AddRequest(@NotNull MediaType mediaType, @NotNull @Positive Integer tmdbId) {
    }

    public record WatchlistItemResponse(
            Long id,
            MediaType mediaType,
            int tmdbId,
            String title,
            String posterUrl,
            Integer year,
            Instant addedAt) {
    }
}
