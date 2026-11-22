package com.moviehub.cinema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Public cinema API shapes. Movie ids ({@code tmdbId}) are TMDB ids, like everywhere else in the public API.
 * {@code localStart}/{@code localEnd} are wall-clock times in the cinema's time zone (what to display);
 * {@code startsAt}/{@code endsAt} are the exact instants.
 */
public final class CinemaDtos {

    private CinemaDtos() {
    }

    public record CinemaSummary(
            long id,
            String name,
            String city,
            String address,
            String postalCode,
            Double latitude,
            Double longitude,
            int roomCount,
            int seatCount,
            List<ScreenType> screenTypes) {
    }

    public record CinemaDetails(
            long id,
            String name,
            String city,
            String address,
            String postalCode,
            String phone,
            String description,
            Double latitude,
            Double longitude,
            String timeZone,
            boolean active,
            List<RoomSummary> rooms) {
    }

    public record RoomSummary(long id, String name, ScreenType screenType, int capacity, boolean active) {
    }

    /** Short reference to a cinema, embedded in screenings. */
    public record CinemaRef(long id, String name, String city, String address, Double latitude, Double longitude,
            String timeZone) {
    }

    public record ScreeningMovie(int tmdbId, String title, String posterUrl, String backdropUrl, Integer runtime,
            Double rating, Integer year) {
    }

    public record Showtime(
            long id,
            Instant startsAt,
            LocalDateTime localStart,
            ScreeningFormat format,
            String language,
            String subtitles,
            long roomId,
            String roomName,
            ScreenType screenType,
            BigDecimal price,
            int seatsLeft) {
    }

    public record MovieShowtimes(ScreeningMovie movie, List<Showtime> showtimes) {
    }

    /**
     * Showtimes of a cinema for one day.
     *
     * @param dates upcoming days (cinema local time) that have at least one showtime
     */
    public record CinemaSchedule(LocalDate date, List<LocalDate> dates, List<MovieShowtimes> movies) {
    }

    public record CinemaShowtimes(CinemaRef cinema, List<Showtime> showtimes) {
    }

    /**
     * Showtimes of a movie in all cinemas for one day.
     *
     * @param cities cities where the movie is showing (before the city filter is applied)
     */
    public record MovieSchedule(LocalDate date, List<LocalDate> dates, List<String> cities,
            List<CinemaShowtimes> cinemas) {
    }

    public record ScreeningDetails(
            long id,
            Instant startsAt,
            Instant endsAt,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            ScreeningFormat format,
            String language,
            String subtitles,
            BigDecimal price,
            BigDecimal vipPrice,
            Screening.Status status,
            boolean bookable,
            ScreeningMovie movie,
            CinemaRef cinema,
            RoomSummary room) {
    }

    public enum SeatStatus {
        AVAILABLE,
        TAKEN
    }

    public record SeatView(long id, String label, String row, int number, int gridRow, int gridColumn,
            SeatType type, SeatStatus status) {
    }

    /** Seat map of a screening: a {@code rows x columns} grid, aisles are the empty cells. */
    public record SeatMap(long screeningId, int rows, int columns, int capacity, int available,
            List<SeatView> seats) {
    }
}
