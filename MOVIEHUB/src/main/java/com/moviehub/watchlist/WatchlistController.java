package com.moviehub.watchlist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.catalog.MediaType;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.security.UserPrincipal;
import com.moviehub.watchlist.WatchlistDtos.AddRequest;
import com.moviehub.watchlist.WatchlistDtos.TitleKey;
import com.moviehub.watchlist.WatchlistDtos.WatchlistItemResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The current user's watchlist. Requires authentication. */
@RestController
@RequestMapping("/api/users/me/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist")
public class WatchlistController {

    private final WatchlistService service;

    @GetMapping
    @Operation(summary = "My watchlist, most recently added first")
    public PageResponse<WatchlistItemResponse> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) MediaType type, @RequestParam(defaultValue = "1") int page) {
        return service.list(user.id(), type, page);
    }

    @GetMapping("/keys")
    @Operation(summary = "All saved titles as (mediaType, tmdbId) pairs, to mark cards as saved")
    public List<TitleKey> keys(@AuthenticationPrincipal UserPrincipal user) {
        return service.keys(user.id());
    }

    @PostMapping
    @Operation(summary = "Add a title (idempotent): 201 when added, 200 when already saved")
    public ResponseEntity<WatchlistItemResponse> add(@AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody AddRequest request) {
        var result = service.add(user.id(), request.mediaType(), request.tmdbId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @DeleteMapping("/{type}/{tmdbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a title")
    public void remove(@AuthenticationPrincipal UserPrincipal user, @PathVariable MediaType type,
            @PathVariable int tmdbId) {
        service.remove(user.id(), type, tmdbId);
    }
}
