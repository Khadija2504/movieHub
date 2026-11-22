package com.moviehub.cinema.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.moviehub.cinema.ScreenType;
import com.moviehub.cinema.Screening;
import com.moviehub.cinema.ScreeningFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AdminCinemaDtos {

    private AdminCinemaDtos() {
    }

    public record CinemaRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 255) String address,
            @Size(max = 20) String postalCode,
            @Size(max = 30) String phone,
            @Size(max = 2000) String description,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            /** IANA zone id, e.g. Europe/Paris. */
            @NotBlank @Size(max = 50) String timeZone,
            @NotNull Boolean active) {
    }

    /**
     * Rectangular layout: rows A.. from the screen, seats 1..n left to right.
     *
     * @param aislesAfter     seat numbers followed by an aisle, e.g. [4, 16]
     * @param vipRows         e.g. ["H", "I"]
     * @param accessibleSeats e.g. ["A1", "A2"]
     */
    public record SeatLayoutRequest(
            @Min(1) @Max(26) int rows,
            @Min(1) @Max(40) int seatsPerRow,
            @Size(max = 10) List<@NotNull @Positive Integer> aislesAfter,
            @Size(max = 26) List<@NotBlank @Pattern(regexp = "[A-Za-z]") String> vipRows,
            @Size(max = 100) List<@NotBlank @Pattern(regexp = "[A-Za-z][0-9]{1,2}") String> accessibleSeats) {
    }

    public record CreateRoomRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull ScreenType screenType,
            @NotNull @Valid SeatLayoutRequest layout) {
    }

    /** The layout of an existing room cannot change (bookings point to its seats). */
    public record UpdateRoomRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull ScreenType screenType,
            @NotNull Boolean active) {
    }

    /**
     * @param movieTmdbId TMDB id: the movie is imported into the catalog if needed
     * @param startsAt    local date-time in the cinema's time zone
     */
    public record ScreeningRequest(
            @NotNull Long roomId,
            @NotNull @Positive Integer movieTmdbId,
            @NotNull LocalDateTime startsAt,
            @NotNull ScreeningFormat format,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2,3}") String language,
            @Pattern(regexp = "[A-Za-z]{2,3}") String subtitles,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal vipPrice) {
    }

    /** A screening as listed in the admin schedule (any status). */
    public record AdminScreening(
            long id,
            long roomId,
            String roomName,
            int movieTmdbId,
            String movieTitle,
            Instant startsAt,
            Instant endsAt,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            ScreeningFormat format,
            String language,
            String subtitles,
            BigDecimal price,
            BigDecimal vipPrice,
            Screening.Status status) {
    }
}
