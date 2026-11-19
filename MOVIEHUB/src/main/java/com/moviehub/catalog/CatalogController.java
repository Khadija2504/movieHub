package com.moviehub.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.catalog.dto.CatalogDtos.Genre;
import com.moviehub.catalog.dto.CatalogDtos.MediaSummary;
import com.moviehub.catalog.dto.CatalogDtos.MovieDetails;
import com.moviehub.catalog.dto.CatalogDtos.SeasonDetails;
import com.moviehub.catalog.dto.CatalogDtos.SeriesDetails;
import com.moviehub.common.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Public, read-only catalog API. All ids are TMDB ids. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Catalog")
@SecurityRequirements
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/movies")
    @Operation(summary = "Browse movies with filters")
    public PageResponse<MediaSummary> movies(BrowseFilter filter) {
        return catalogService.browse(MediaType.MOVIE, filter);
    }

    @GetMapping("/movies/{id}")
    @Operation(summary = "Movie details: cast, trailer, recommendations and MovieHub offer")
    public MovieDetails movie(@PathVariable int id) {
        return catalogService.movie(id);
    }

    @GetMapping("/series")
    @Operation(summary = "Browse TV series with filters")
    public PageResponse<MediaSummary> series(BrowseFilter filter) {
        return catalogService.browse(MediaType.SERIES, filter);
    }

    @GetMapping("/series/{id}")
    @Operation(summary = "Series details: seasons, cast, trailer and MovieHub offer")
    public SeriesDetails seriesDetails(@PathVariable int id) {
        return catalogService.series(id);
    }

    @GetMapping("/series/{id}/seasons/{seasonNumber}")
    @Operation(summary = "Episodes of one season")
    public SeasonDetails season(@PathVariable int id, @PathVariable int seasonNumber) {
        return catalogService.season(id, seasonNumber);
    }

    @GetMapping("/trending")
    @Operation(summary = "Trending titles")
    public PageResponse<MediaSummary> trending(
            @Parameter(description = "MOVIE, SERIES, or omit for both") @RequestParam(required = false) MediaType type,
            @RequestParam(defaultValue = "false") boolean daily,
            @RequestParam(defaultValue = "1") int page) {
        return catalogService.trending(type, daily, page);
    }

    @GetMapping("/search")
    @Operation(summary = "Search movies and series by title")
    public PageResponse<MediaSummary> search(@RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(defaultValue = "1") int page) {
        return catalogService.search(query, page);
    }

    @GetMapping("/genres")
    @Operation(summary = "Genres for movies or series")
    public List<Genre> genres(@RequestParam(defaultValue = "MOVIE") MediaType type) {
        return catalogService.genres(type);
    }
}
