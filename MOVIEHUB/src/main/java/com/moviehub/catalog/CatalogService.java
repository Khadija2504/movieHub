package com.moviehub.catalog;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.catalog.dto.CatalogDtos.Genre;
import com.moviehub.catalog.dto.CatalogDtos.MediaSummary;
import com.moviehub.catalog.dto.CatalogDtos.MovieDetails;
import com.moviehub.catalog.dto.CatalogDtos.MovieOffer;
import com.moviehub.catalog.dto.CatalogDtos.SeasonDetails;
import com.moviehub.catalog.dto.CatalogDtos.SeriesDetails;
import com.moviehub.catalog.dto.CatalogDtos.SeriesOffer;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.common.dto.PageResponse;

import java.util.List;

/**
 * Public catalog: browsing, search and details come live from TMDB (cached);
 * details are enriched with the MovieHub offer from the local movies / series tables.
 */
@Service
public class CatalogService {

    /** Minimum vote count for rating-based sorts, so obscure titles with 3 votes of 10/10 don't top the list. */
    private static final int MIN_VOTES_TOP_RATED = 300;
    private static final int MIN_VOTES_OTHER_SORTS = 20;
    private static final int MAX_QUERY_LENGTH = 100;

    private final TmdbClient tmdb;
    private final TmdbMapper mapper;
    private final MovieRepository movieRepository;
    private final SeriesRepository seriesRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final Clock clock;

    public CatalogService(TmdbClient tmdb, TmdbMapper mapper, MovieRepository movieRepository,
            SeriesRepository seriesRepository, VideoSourceRepository videoSourceRepository) {
        this.tmdb = tmdb;
        this.mapper = mapper;
        this.movieRepository = movieRepository;
        this.seriesRepository = seriesRepository;
        this.videoSourceRepository = videoSourceRepository;
        this.clock = Clock.systemUTC();
    }

    // ---------------------------------------------------------------- browse

    public PageResponse<MediaSummary> browse(MediaType type, BrowseFilter filter) {
        return mapper.toPage(tmdb.discover(type.tmdbType(), discoverParams(type, filter)), type);
    }

    /** Builds TMDB /discover parameters. A sorted map keeps the cache key stable. */
    Map<String, Object> discoverParams(MediaType type, BrowseFilter filter) {
        boolean movie = type == MediaType.MOVIE;
        String dateField = movie ? "primary_release_date" : "first_air_date";
        Map<String, Object> params = new TreeMap<>();
        params.put("page", filter.pageOrDefault());
        params.put("include_adult", false);

        switch (filter.sortOrDefault()) {
            case POPULAR -> params.put("sort_by", "popularity.desc");
            case TOP_RATED -> {
                params.put("sort_by", "vote_average.desc");
                params.put("vote_count.gte", MIN_VOTES_TOP_RATED);
            }
            case NEWEST -> {
                params.put("sort_by", dateField + ".desc");
                params.put(dateField + ".lte", LocalDate.now(clock).toString()); // no unreleased titles
                params.put("vote_count.gte", MIN_VOTES_OTHER_SORTS);
            }
            case TITLE -> {
                params.put("sort_by", movie ? "title.asc" : "name.asc");
                params.put("vote_count.gte", MIN_VOTES_OTHER_SORTS);
            }
        }
        if (filter.genre() != null) {
            params.put("with_genres", filter.genre());
        }
        if (filter.year() != null) {
            params.put(movie ? "primary_release_year" : "first_air_date_year", filter.year());
        }
        if (filter.minRating() != null) {
            params.put("vote_average.gte", Math.max(0.0, Math.min(filter.minRating(), 10.0)));
            params.putIfAbsent("vote_count.gte", MIN_VOTES_OTHER_SORTS);
        }
        return params;
    }

    /** @param type null = movies and series */
    public PageResponse<MediaSummary> trending(MediaType type, boolean daily, int page) {
        String tmdbType = type == null ? "all" : type.tmdbType();
        return mapper.toPage(tmdb.trending(tmdbType, daily ? "day" : "week", clampPage(page)), null);
    }

    /** Searches movies and series by title. People in TMDB results are dropped. */
    public PageResponse<MediaSummary> search(String query, int page) {
        if (!StringUtils.hasText(query)) {
            return PageResponse.empty();
        }
        String q = query.strip();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        return mapper.toPage(tmdb.searchMulti(q, clampPage(page)), null);
    }

    public List<Genre> genres(MediaType type) {
        return mapper.toGenres(tmdb.genres(type.tmdbType()).genres());
    }

    // ---------------------------------------------------------------- details

    @Transactional(readOnly = true)
    public MovieDetails movie(int tmdbId) {
        MovieOffer offer = movieRepository.findByTmdbId(tmdbId)
                .filter(Movie::isActive)
                .map(m -> {
                    // Streamable only once a video is attached.
                    boolean streamable = m.isStreamable() && videoSourceRepository.existsByMovieId(m.getId());
                    return new MovieOffer(true, streamable, streamable && m.isInSubscription(),
                            streamable ? m.getPurchasePrice() : null, streamable ? m.getRentalPrice() : null);
                })
                .orElse(MovieOffer.NONE);
        return mapper.toMovieDetails(tmdb.movie(tmdbId), offer);
    }

    @Transactional(readOnly = true)
    public SeriesDetails series(int tmdbId) {
        SeriesOffer offer = seriesRepository.findByTmdbId(tmdbId)
                .filter(Series::isActive)
                .map(s -> new SeriesOffer(true, s.isStreamable()))
                .orElse(SeriesOffer.NONE);
        return mapper.toSeriesDetails(tmdb.series(tmdbId), offer);
    }

    public SeasonDetails season(int seriesTmdbId, int seasonNumber) {
        return mapper.toSeasonDetails(seriesTmdbId, tmdb.season(seriesTmdbId, seasonNumber));
    }

    private static int clampPage(int page) {
        return Math.max(1, Math.min(page, TmdbMapper.MAX_PAGE));
    }
}
