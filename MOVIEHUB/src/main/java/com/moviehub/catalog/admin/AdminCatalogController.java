package com.moviehub.catalog.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.catalog.admin.AdminCatalogDtos.CatalogMovie;
import com.moviehub.catalog.admin.AdminCatalogDtos.CatalogSeries;
import com.moviehub.catalog.admin.AdminCatalogDtos.MovieOfferRequest;
import com.moviehub.catalog.admin.AdminCatalogDtos.SeriesOfferRequest;
import com.moviehub.common.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADMIN only (secured by the /api/admin/** rule). Movie/series ids in paths are local ids, except for imports. */
@RestController
@RequestMapping("/api/admin/catalog")
@RequiredArgsConstructor
@Tag(name = "Admin · Catalog")
public class AdminCatalogController {

    private final CatalogAdminService service;

    @GetMapping("/movies")
    @Operation(summary = "List imported movies")
    public PageResponse<CatalogMovie> movies(@RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listMovies(q, pageable);
    }

    @PostMapping("/movies/import/{tmdbId}")
    @Operation(summary = "Import (or refresh) a movie from TMDB")
    public CatalogMovie importMovie(@PathVariable int tmdbId) {
        return service.importMovie(tmdbId);
    }

    @PutMapping("/movies/{id}/offer")
    @Operation(summary = "Set streaming availability, prices and video of a movie")
    public CatalogMovie updateMovieOffer(@PathVariable long id, @Valid @RequestBody MovieOfferRequest request) {
        return service.updateMovieOffer(id, request);
    }

    @GetMapping("/series")
    @Operation(summary = "List imported series")
    public PageResponse<CatalogSeries> series(@RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listSeries(q, pageable);
    }

    @PostMapping("/series/import/{tmdbId}")
    @Operation(summary = "Import (or refresh) a series from TMDB")
    public CatalogSeries importSeries(@PathVariable int tmdbId) {
        return service.importSeries(tmdbId);
    }

    @PutMapping("/series/{id}/offer")
    @Operation(summary = "Set streaming availability of a series")
    public CatalogSeries updateSeriesOffer(@PathVariable long id, @Valid @RequestBody SeriesOfferRequest request) {
        return service.updateSeriesOffer(id, request);
    }
}
