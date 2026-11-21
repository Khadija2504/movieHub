package com.moviehub.review;

import java.time.Instant;
import java.util.List;

import com.moviehub.catalog.MediaType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public static final int MAX_COMMENT = 2000;

    public record ReviewRequest(
            @NotNull(message = "Rating is required")
            @Min(value = 1, message = "Rating must be between 1 and 10")
            @Max(value = 10, message = "Rating must be between 1 and 10")
            Integer rating,

            @Size(max = MAX_COMMENT, message = "Review must be at most " + MAX_COMMENT + " characters")
            String comment) {
    }

    /** Public author info: never exposes the email. */
    public record Author(Long id, String displayName, String avatarUrl) {
    }

    public record ReviewResponse(
            Long id,
            MediaType mediaType,
            int tmdbId,
            String title,
            String posterUrl,
            int rating,
            String comment,
            Author author,
            boolean edited,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * @param average      null when there are no reviews
     * @param distribution counts for ratings 1..10 (index 0 = rating 1)
     */
    public record ReviewSummary(Double average, long count, List<Long> distribution) {
    }
}
