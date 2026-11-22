package com.moviehub.cinema;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.catalog.dto.CatalogDtos.MediaSummary;
import com.moviehub.cinema.CinemaDtos.CinemaDetails;
import com.moviehub.cinema.CinemaDtos.CinemaSchedule;
import com.moviehub.cinema.CinemaDtos.CinemaSummary;
import com.moviehub.cinema.CinemaDtos.MovieSchedule;
import com.moviehub.cinema.CinemaDtos.ScreeningDetails;
import com.moviehub.cinema.CinemaDtos.SeatMap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Public, read-only cinema endpoints. Dates are local to the cinema (yyyy-MM-dd). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Cinemas")
public class CinemaController {

    private final CinemaService service;

    @GetMapping("/cinemas")
    @Operation(summary = "Active cinemas, optionally in one city")
    public List<CinemaSummary> cinemas(@RequestParam(required = false) String city) {
        return service.list(city);
    }

    @GetMapping("/cinemas/cities")
    @Operation(summary = "Cities that have at least one active cinema")
    public List<String> cities() {
        return service.cities();
    }

    @GetMapping("/cinemas/{id}")
    @Operation(summary = "Cinema details with its rooms")
    public CinemaDetails cinema(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/cinemas/{id}/screenings")
    @Operation(summary = "Showtimes of a cinema for a day, grouped by movie (default: first day with showtimes)")
    public CinemaSchedule cinemaScreenings(@PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.schedule(id, date);
    }

    @GetMapping("/movies/now-showing")
    @Operation(summary = "Movies with upcoming screenings")
    public List<MediaSummary> nowShowing() {
        return service.nowShowing();
    }

    @GetMapping("/movies/{tmdbId}/screenings")
    @Operation(summary = "Showtimes of a movie for a day, grouped by cinema (default: first day with showtimes)")
    public MovieSchedule movieScreenings(@PathVariable int tmdbId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String city) {
        return service.movieSchedule(tmdbId, date, city);
    }

    @GetMapping("/screenings/{id}")
    @Operation(summary = "Screening details")
    public ScreeningDetails screening(@PathVariable long id) {
        return service.screening(id);
    }

    @GetMapping("/screenings/{id}/seats")
    @Operation(summary = "Seat map of a screening with the status of every seat")
    public SeatMap seats(@PathVariable long id) {
        return service.seats(id);
    }
}
