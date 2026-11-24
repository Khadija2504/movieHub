package com.moviehub.streaming;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.common.dto.PageResponse;
import com.moviehub.security.UserPrincipal;
import com.moviehub.streaming.StreamService.LibraryFilter;
import com.moviehub.streaming.StreamingDtos.LibraryEntry;
import com.moviehub.streaming.StreamingDtos.WatchedMovie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The current user's library, watch history and "Continue watching". */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Library & history")
public class MyStreamingController {

    private static final int CONTINUE_WATCHING_MAX = 20;

    private final StreamService service;

    @GetMapping("/library")
    @Operation(summary = "Movies I bought or rented, most recent first")
    public PageResponse<LibraryEntry> library(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "ALL") LibraryFilter filter, @RequestParam(defaultValue = "1") int page) {
        return service.library(user.id(), filter, page);
    }

    @GetMapping("/history")
    @Operation(summary = "Movies I watched, most recent first")
    public PageResponse<WatchedMovie> history(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "1") int page) {
        return service.history(user.id(), page);
    }

    @DeleteMapping("/history/{tmdbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a movie from my history (and its resume point)")
    public void removeFromHistory(@AuthenticationPrincipal UserPrincipal user, @PathVariable int tmdbId) {
        service.removeFromHistory(user.id(), tmdbId);
    }

    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear my watch history")
    public void clearHistory(@AuthenticationPrincipal UserPrincipal user) {
        service.clearHistory(user.id());
    }

    @GetMapping("/continue-watching")
    @Operation(summary = "Movies I started and can still watch")
    public List<WatchedMovie> continueWatching(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "12") int limit) {
        return service.continueWatching(user.id(), Math.max(1, Math.min(limit, CONTINUE_WATCHING_MAX)));
    }
}
