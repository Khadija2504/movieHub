package com.moviehub.catalog.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.Series;
import com.moviehub.catalog.SeriesRepository;
import com.moviehub.catalog.admin.AdminCatalogDtos.CatalogMovie;
import com.moviehub.catalog.admin.AdminCatalogDtos.CatalogSeries;
import com.moviehub.catalog.admin.AdminCatalogDtos.MovieOfferRequest;
import com.moviehub.catalog.admin.AdminCatalogDtos.SeriesOfferRequest;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.catalog.tmdb.TmdbModels;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/** Import titles from TMDB into the local catalog and manage their MovieHub offer. */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private final TmdbClient tmdb;
    private final TmdbMapper mapper;
    private final MovieRepository movieRepository;
    private final SeriesRepository seriesRepository;

    // ---------------------------------------------------------------- movies

    /** Creates the movie, or refreshes its metadata if it was already imported (offer is kept). */
    @Transactional
    public CatalogMovie importMovie(int tmdbId) {
        TmdbModels.MovieDetails details = tmdb.movie(tmdbId);
        Movie movie = movieRepository.findByTmdbId(tmdbId).orElseGet(() -> new Movie(tmdbId));
        movie.setTitle(details.title());
        movie.setOverview(details.overview());
        movie.setPosterPath(details.posterPath());
        movie.setBackdropPath(details.backdropPath());
        movie.setReleaseDate(parse(details.releaseDate()));
        movie.setRuntime(details.runtime());
        movie.setVoteAverage(details.voteAverage());
        return toDto(movieRepository.save(movie));
    }

    @Transactional
    public CatalogMovie updateMovieOffer(long id, MovieOfferRequest request) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Movie", id));
        movie.setStreamable(request.streamable());
        movie.setPurchasePrice(request.purchasePrice());
        movie.setRentalPrice(request.rentalPrice());
        movie.setVideoUrl(StringUtils.hasText(request.videoUrl()) ? request.videoUrl().strip() : null);
        movie.setActive(request.active());
        return toDto(movie);
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogMovie> listMovies(String query, Pageable pageable) {
        var page = StringUtils.hasText(query)
                ? movieRepository.findByTitleContainingIgnoreCase(query.strip(), pageable)
                : movieRepository.findAll(pageable);
        return PageResponse.of(page, this::toDto);
    }

    // ---------------------------------------------------------------- series

    @Transactional
    public CatalogSeries importSeries(int tmdbId) {
        TmdbModels.SeriesDetails details = tmdb.series(tmdbId);
        Series series = seriesRepository.findByTmdbId(tmdbId).orElseGet(() -> new Series(tmdbId));
        series.setTitle(details.name());
        series.setOverview(details.overview());
        series.setPosterPath(details.posterPath());
        series.setBackdropPath(details.backdropPath());
        series.setFirstAirDate(parse(details.firstAirDate()));
        series.setNumberOfSeasons(details.numberOfSeasons());
        series.setVoteAverage(details.voteAverage());
        return toDto(seriesRepository.save(series));
    }

    @Transactional
    public CatalogSeries updateSeriesOffer(long id, SeriesOfferRequest request) {
        Series series = seriesRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Series", id));
        series.setStreamable(request.streamable());
        series.setActive(request.active());
        return toDto(series);
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogSeries> listSeries(String query, Pageable pageable) {
        var page = StringUtils.hasText(query)
                ? seriesRepository.findByTitleContainingIgnoreCase(query.strip(), pageable)
                : seriesRepository.findAll(pageable);
        return PageResponse.of(page, this::toDto);
    }

    // ---------------------------------------------------------------- mapping

    private CatalogMovie toDto(Movie m) {
        return new CatalogMovie(m.getId(), m.getTmdbId(), m.getTitle(), mapper.image(m.getPosterPath(), "w185"),
                m.getReleaseDate(), m.isStreamable(), m.getPurchasePrice(), m.getRentalPrice(), m.getVideoUrl(),
                m.isActive(), m.getCreatedAt());
    }

    private CatalogSeries toDto(Series s) {
        return new CatalogSeries(s.getId(), s.getTmdbId(), s.getTitle(), mapper.image(s.getPosterPath(), "w185"),
                s.getFirstAirDate(), s.getNumberOfSeasons(), s.isStreamable(), s.isActive(), s.getCreatedAt());
    }

    private static java.time.LocalDate parse(String date) {
        try {
            return StringUtils.hasText(date) ? java.time.LocalDate.parse(date) : null;
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
