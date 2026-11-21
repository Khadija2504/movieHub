package com.moviehub.review;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.catalog.MediaType;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.review.ReviewDtos.ReviewRequest;
import com.moviehub.review.ReviewDtos.ReviewResponse;
import com.moviehub.review.ReviewDtos.ReviewSummary;
import com.moviehub.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Reviews of a title, identified by (type, tmdbId). Reading is public; writing requires login.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService service;

    @GetMapping("/api/reviews/{type}/{tmdbId}")
    @SecurityRequirements
    @Operation(summary = "Reviews of a title, newest first")
    public PageResponse<ReviewResponse> forTitle(@PathVariable MediaType type, @PathVariable int tmdbId,
            @RequestParam(defaultValue = "1") int page) {
        return service.forTitle(type, tmdbId, page);
    }

    @GetMapping("/api/reviews/{type}/{tmdbId}/summary")
    @SecurityRequirements
    @Operation(summary = "Average community rating and rating distribution")
    public ReviewSummary summary(@PathVariable MediaType type, @PathVariable int tmdbId) {
        return service.summary(type, tmdbId);
    }

    @GetMapping("/api/reviews/{type}/{tmdbId}/mine")
    @Operation(summary = "My review of a title (204 if I haven't reviewed it)")
    public ResponseEntity<ReviewResponse> mine(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable MediaType type, @PathVariable int tmdbId) {
        return service.mine(user.id(), type, tmdbId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/api/reviews/{type}/{tmdbId}")
    @Operation(summary = "Create or update my review of a title")
    public ReviewResponse upsert(@AuthenticationPrincipal UserPrincipal user, @PathVariable MediaType type,
            @PathVariable int tmdbId, @Valid @RequestBody ReviewRequest request) {
        return service.upsert(user.id(), type, tmdbId, request);
    }

    @DeleteMapping("/api/reviews/{type}/{tmdbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my review of a title")
    public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable MediaType type,
            @PathVariable int tmdbId) {
        service.delete(user.id(), type, tmdbId);
    }

    @GetMapping("/api/users/me/reviews")
    @Operation(summary = "All my reviews, most recently updated first")
    public PageResponse<ReviewResponse> myReviews(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "1") int page) {
        return service.byUser(user.id(), page);
    }
}
