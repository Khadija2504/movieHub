package com.moviehub.catalog.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminCatalogDtos {

    private AdminCatalogDtos() {
    }

    /** A movie of the local catalog, as shown in the admin pages. {@code id} is the local id. */
    public record CatalogMovie(
            Long id,
            int tmdbId,
            String title,
            String posterUrl,
            LocalDate releaseDate,
            boolean streamable,
            boolean inSubscription,
            BigDecimal purchasePrice,
            BigDecimal rentalPrice,
            List<VideoSourceDto> videoSources,
            String videoCredit,
            boolean active,
            Instant createdAt) {
    }

    /**
     * A video file of a movie.
     *
     * @param url     https://... (MP4 or HLS .m3u8) or local:&lt;path under the videos directory&gt;
     * @param quality picture height of an MP4 (480, 720, 1080); null for an adaptive HLS stream
     */
    public record VideoSourceDto(
            @NotBlank(message = "Video URL is required") @Size(max = 500) String url,
            @Min(100) @Max(4320) Integer quality) {
    }

    public record CatalogSeries(
            Long id,
            int tmdbId,
            String title,
            String posterUrl,
            LocalDate firstAirDate,
            Integer numberOfSeasons,
            boolean streamable,
            boolean active,
            Instant createdAt) {
    }

    /**
     * Prices are optional: null = not for sale / not for rent.
     *
     * @param inSubscription null = true (included with a subscription)
     * @param videoSources   null = keep the current videos, [] = remove them
     */
    public record MovieOfferRequest(
            @NotNull Boolean streamable,
            @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal purchasePrice,
            @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal rentalPrice,
            Boolean inSubscription,
            @Size(max = 6, message = "At most 6 video files") List<@Valid VideoSourceDto> videoSources,
            @Size(max = 255) String videoCredit,
            @NotNull Boolean active) {
    }

    public record SeriesOfferRequest(@NotNull Boolean streamable, @NotNull Boolean active) {
    }
}
