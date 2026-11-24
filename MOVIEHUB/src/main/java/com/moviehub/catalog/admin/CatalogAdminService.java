package com.moviehub.catalog.admin;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.moviehub.catalog.Movie;
import com.moviehub.catalog.MovieRepository;
import com.moviehub.catalog.Series;
import com.moviehub.catalog.SeriesRepository;
import com.moviehub.catalog.VideoSource;
import com.moviehub.catalog.VideoSourceRepository;
import com.moviehub.catalog.admin.AdminCatalogDtos.CatalogMovie;
import com.moviehub.catalog.admin.AdminCatalogDtos.CatalogSeries;
import com.moviehub.catalog.admin.AdminCatalogDtos.MovieOfferRequest;
import com.moviehub.catalog.admin.AdminCatalogDtos.SeriesOfferRequest;
import com.moviehub.catalog.admin.AdminCatalogDtos.VideoSourceDto;
import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.catalog.tmdb.TmdbModels;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.common.exception.BadRequestException;
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
    private final VideoSourceRepository videoSourceRepository;

    private static final Pattern LOCAL_PATH = Pattern.compile("[A-Za-z0-9_-]+(/[A-Za-z0-9_.-]+)+");

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
        movie.setInSubscription(request.inSubscription() == null || request.inSubscription());
        movie.setVideoCredit(StringUtils.hasText(request.videoCredit()) ? request.videoCredit().strip() : null);
        movie.setActive(request.active());
        if (request.videoSources() != null) {
            replaceVideos(movie, request.videoSources());
        }
        return toDto(movie);
    }

    private void replaceVideos(Movie movie, List<VideoSourceDto> sources) {
        long hls = sources.stream().filter(s -> isHls(s.url())).count();
        if (hls > 1) {
            throw new BadRequestException("A movie can have only one HLS stream");
        }
        for (VideoSourceDto source : sources) {
            validateVideo(source);
        }
        videoSourceRepository.deleteByMovieId(movie.getId());
        videoSourceRepository.flush();
        for (VideoSourceDto source : sources) {
            videoSourceRepository.save(new VideoSource(movie, source.url().strip(), source.quality()));
        }
    }

    /** https://host/file.mp4|.m3u8, or local:folder/file.mp4|.m3u8 (no "..", no absolute path). */
    static void validateVideo(VideoSourceDto source) {
        String url = source.url().strip();
        boolean local = url.startsWith(VideoSource.LOCAL_PREFIX);
        if (local) {
            String path = url.substring(VideoSource.LOCAL_PREFIX.length());
            if (!LOCAL_PATH.matcher(path).matches() || path.contains("..")) {
                throw new BadRequestException("Local videos look like local:folder/video.mp4 (letters, digits, - _ . /)");
            }
        } else {
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid video URL: " + url);
            }
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new BadRequestException("Video URLs must start with https:// (or local: for stored files)");
            }
        }
        String path = local ? url : URI.create(url).getPath();
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".m3u8")) {
            if (source.quality() != null) {
                throw new BadRequestException("An HLS stream has no fixed quality: leave the quality empty");
            }
        } else if (lower.endsWith(".mp4")) {
            if (source.quality() == null) {
                throw new BadRequestException("Give the picture height (e.g. 720) of every MP4 file");
            }
        } else {
            throw new BadRequestException("Videos must be MP4 files or HLS playlists (.m3u8)");
        }
    }

    private static boolean isHls(String url) {
        String path = url.strip();
        int query = path.indexOf('?');
        return (query < 0 ? path : path.substring(0, query)).toLowerCase(Locale.ROOT).endsWith(".m3u8");
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
        List<VideoSourceDto> videos = m.getId() == null ? List.of()
                : videoSourceRepository.findByMovieId(m.getId()).stream()
                        .map(v -> new VideoSourceDto(v.getUrl(), v.getQuality())).toList();
        return new CatalogMovie(m.getId(), m.getTmdbId(), m.getTitle(), mapper.image(m.getPosterPath(), "w185"),
                m.getReleaseDate(), m.isStreamable(), m.isInSubscription(), m.getPurchasePrice(), m.getRentalPrice(),
                videos, m.getVideoCredit(), m.isActive(), m.getCreatedAt());
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
