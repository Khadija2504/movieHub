package com.moviehub.catalog.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
            BigDecimal purchasePrice,
            BigDecimal rentalPrice,
            String videoUrl,
            boolean active,
            Instant createdAt) {
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

    /** Prices are optional: null = not for sale / not for rent. */
    public record MovieOfferRequest(
            @NotNull Boolean streamable,
            @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal purchasePrice,
            @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal rentalPrice,
            @Size(max = 500) String videoUrl,
            @NotNull Boolean active) {
    }

    public record SeriesOfferRequest(@NotNull Boolean streamable, @NotNull Boolean active) {
    }
}
