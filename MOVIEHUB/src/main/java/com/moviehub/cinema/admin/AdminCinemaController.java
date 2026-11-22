package com.moviehub.cinema.admin;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.cinema.CinemaDtos.CinemaDetails;
import com.moviehub.cinema.CinemaDtos.RoomSummary;
import com.moviehub.cinema.admin.AdminCinemaDtos.AdminScreening;
import com.moviehub.cinema.admin.AdminCinemaDtos.CinemaRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.CreateRoomRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.ScreeningRequest;
import com.moviehub.cinema.admin.AdminCinemaDtos.UpdateRoomRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADMIN only (secured by the /api/admin/** rule). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin · Cinemas")
public class AdminCinemaController {

    private final CinemaAdminService service;

    @GetMapping("/cinemas")
    @Operation(summary = "All cinemas, including inactive ones")
    public List<CinemaDetails> cinemas() {
        return service.listCinemas();
    }

    @GetMapping("/cinemas/{id}")
    @Operation(summary = "A cinema with all its rooms")
    public CinemaDetails cinema(@PathVariable long id) {
        return service.getCinema(id);
    }

    @PostMapping("/cinemas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a cinema")
    public CinemaDetails createCinema(@Valid @RequestBody CinemaRequest request) {
        return service.createCinema(request);
    }

    @PutMapping("/cinemas/{id}")
    @Operation(summary = "Update a cinema (set active=false to hide it)")
    public CinemaDetails updateCinema(@PathVariable long id, @Valid @RequestBody CinemaRequest request) {
        return service.updateCinema(id, request);
    }

    @PostMapping("/cinemas/{id}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a room and generate its seats from a layout")
    public RoomSummary createRoom(@PathVariable long id, @Valid @RequestBody CreateRoomRequest request) {
        return service.createRoom(id, request);
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Rename a room, change its screen type or (de)activate it")
    public RoomSummary updateRoom(@PathVariable long id, @Valid @RequestBody UpdateRoomRequest request) {
        return service.updateRoom(id, request);
    }

    @DeleteMapping("/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a room that never had a screening")
    public void deleteRoom(@PathVariable long id) {
        service.deleteRoom(id);
    }

    @GetMapping("/cinemas/{id}/screenings")
    @Operation(summary = "Schedule of a cinema for a local day, including cancelled screenings")
    public List<AdminScreening> screenings(@PathVariable long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.screenings(id, date);
    }

    @PostMapping("/screenings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a screening (startsAt is local to the cinema; rejects overlaps)")
    public AdminScreening createScreening(@Valid @RequestBody ScreeningRequest request) {
        return service.createScreening(request);
    }

    @PostMapping("/screenings/{id}/cancel")
    @Operation(summary = "Cancel an upcoming screening")
    public AdminScreening cancelScreening(@PathVariable long id) {
        return service.cancelScreening(id);
    }
}
