package com.moviehub.catalog.tmdb;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.moviehub.catalog.MediaType;
import com.moviehub.catalog.dto.CatalogDtos;
import com.moviehub.catalog.dto.CatalogDtos.MediaSummary;
import com.moviehub.catalog.dto.CatalogDtos.MovieOffer;
import com.moviehub.catalog.dto.CatalogDtos.Person;
import com.moviehub.catalog.dto.CatalogDtos.SeriesOffer;
import com.moviehub.catalog.dto.CatalogDtos.Trailer;
import com.moviehub.catalog.tmdb.TmdbModels.CastMember;
import com.moviehub.catalog.tmdb.TmdbModels.Credits;
import com.moviehub.catalog.tmdb.TmdbModels.MediaItem;
import com.moviehub.catalog.tmdb.TmdbModels.Page;
import com.moviehub.catalog.tmdb.TmdbModels.Video;
import com.moviehub.common.dto.PageResponse;
import com.moviehub.config.AppProperties;

/** Converts raw TMDB responses into MovieHub DTOs (full image URLs, parsed dates, trimmed lists). */
@Component
public class TmdbMapper {

    /** TMDB never serves more than 500 pages. */
    public static final int MAX_PAGE = 500;
    static final int MAX_CAST = 15;
    static final int MAX_RECOMMENDATIONS = 12;

    private static final String POSTER = "w500";
    private static final String BACKDROP = "w1280";
    private static final String PROFILE = "w185";
    private static final String STILL = "w300";

    private final String imageBaseUrl;

    public TmdbMapper(AppProperties props) {
        this.imageBaseUrl = props.tmdb().imageBaseUrl();
    }

    // ---------------------------------------------------------------- lists

    /** @param fallbackType used when the item has no media_type (discover endpoints); null = read it from the item */
    public PageResponse<MediaSummary> toPage(Page<MediaItem> page, MediaType fallbackType) {
        if (page == null || page.results() == null) {
            return PageResponse.empty();
        }
        List<MediaSummary> content = toSummaries(page.results(), fallbackType);
        return new PageResponse<>(content, page.page(), Math.min(page.totalPages(), MAX_PAGE), page.totalResults());
    }

    public List<MediaSummary> toSummaries(List<MediaItem> items, MediaType fallbackType) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> toSummary(item, fallbackType))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Empty for people and unknown types (search/multi and trending/all can return them). */
    public Optional<MediaSummary> toSummary(MediaItem item, MediaType fallbackType) {
        MediaType type = item.mediaType() != null ? MediaType.fromTmdb(item.mediaType()) : fallbackType;
        if (type == null) {
            return Optional.empty();
        }
        boolean movie = type == MediaType.MOVIE;
        LocalDate date = parseDate(movie ? item.releaseDate() : item.firstAirDate());
        return Optional.of(new MediaSummary(
                item.id(),
                type,
                movie ? item.title() : item.name(),
                item.overview(),
                image(item.posterPath(), POSTER),
                image(item.backdropPath(), BACKDROP),
                date,
                date == null ? null : date.getYear(),
                round(item.voteAverage()),
                item.voteCount(),
                item.genreIds() == null ? List.of() : item.genreIds()));
    }

    public List<CatalogDtos.Genre> toGenres(List<TmdbModels.Genre> genres) {
        return genres == null ? List.of() : genres.stream().map(g -> new CatalogDtos.Genre(g.id(), g.name())).toList();
    }

    // ---------------------------------------------------------------- details

    public CatalogDtos.MovieDetails toMovieDetails(TmdbModels.MovieDetails m, MovieOffer offer) {
        LocalDate releaseDate = parseDate(m.releaseDate());
        return new CatalogDtos.MovieDetails(
                m.id(), m.title(), emptyToNull(m.tagline()), m.overview(),
                image(m.posterPath(), POSTER), image(m.backdropPath(), BACKDROP),
                releaseDate, releaseDate == null ? null : releaseDate.getYear(),
                m.runtime() != null && m.runtime() > 0 ? m.runtime() : null,
                m.status(), m.originalLanguage(),
                round(m.voteAverage()), m.voteCount(),
                toGenres(m.genres()),
                crew(m.credits(), "Director"),
                cast(m.credits()),
                trailer(m.videos()),
                recommendations(m.recommendations(), MediaType.MOVIE),
                offer == null ? MovieOffer.NONE : offer);
    }

    public CatalogDtos.SeriesDetails toSeriesDetails(TmdbModels.SeriesDetails s, SeriesOffer offer) {
        LocalDate firstAir = parseDate(s.firstAirDate());
        Integer runtime = s.episodeRunTime() == null || s.episodeRunTime().isEmpty() ? null : s.episodeRunTime().get(0);
        List<CatalogDtos.SeasonSummary> seasons = s.seasons() == null ? List.of() : s.seasons().stream()
                // Season 0 = "Specials": keep it, but list it last.
                .sorted(Comparator.comparingInt(season -> season.seasonNumber() == 0 ? Integer.MAX_VALUE : season.seasonNumber()))
                .map(season -> new CatalogDtos.SeasonSummary(season.seasonNumber(), season.name(),
                        emptyToNull(season.overview()), season.episodeCount(), parseDate(season.airDate()),
                        image(season.posterPath(), POSTER)))
                .toList();
        List<Person> creators = s.createdBy() == null ? List.of() : s.createdBy().stream()
                .map(c -> new Person(c.id(), c.name(), "Creator", image(c.profilePath(), PROFILE)))
                .toList();

        return new CatalogDtos.SeriesDetails(
                s.id(), s.name(), emptyToNull(s.tagline()), s.overview(),
                image(s.posterPath(), POSTER), image(s.backdropPath(), BACKDROP),
                firstAir, parseDate(s.lastAirDate()), firstAir == null ? null : firstAir.getYear(),
                s.status(), s.numberOfSeasons(), s.numberOfEpisodes(), runtime,
                round(s.voteAverage()), s.voteCount(),
                toGenres(s.genres()), creators, cast(s.credits()), trailer(s.videos()), seasons,
                recommendations(s.recommendations(), MediaType.SERIES),
                offer == null ? SeriesOffer.NONE : offer);
    }

    public CatalogDtos.SeasonDetails toSeasonDetails(int seriesId, TmdbModels.Season season) {
        List<CatalogDtos.Episode> episodes = season.episodes() == null ? List.of() : season.episodes().stream()
                .map(e -> new CatalogDtos.Episode(e.episodeNumber(), e.name(), emptyToNull(e.overview()),
                        parseDate(e.airDate()), e.runtime(), image(e.stillPath(), STILL), round(e.voteAverage())))
                .toList();
        return new CatalogDtos.SeasonDetails(seriesId, season.seasonNumber(), season.name(),
                emptyToNull(season.overview()), parseDate(season.airDate()), image(season.posterPath(), POSTER),
                episodes);
    }

    // ---------------------------------------------------------------- helpers

    public String image(String path, String size) {
        return StringUtils.hasText(path) ? imageBaseUrl + "/" + size + path : null;
    }

    private List<Person> cast(Credits credits) {
        if (credits == null || credits.cast() == null) {
            return List.of();
        }
        return credits.cast().stream()
                .sorted(Comparator.comparing(CastMember::order, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_CAST)
                .map(c -> new Person(c.id(), c.name(), emptyToNull(c.character()), image(c.profilePath(), PROFILE)))
                .toList();
    }

    private List<Person> crew(Credits credits, String job) {
        if (credits == null || credits.crew() == null) {
            return List.of();
        }
        return credits.crew().stream()
                .filter(c -> job.equals(c.job()))
                .map(c -> new Person(c.id(), c.name(), job, image(c.profilePath(), PROFILE)))
                .distinct()
                .toList();
    }

    private List<MediaSummary> recommendations(Page<MediaItem> page, MediaType type) {
        if (page == null) {
            return List.of();
        }
        return toSummaries(page.results(), type).stream().limit(MAX_RECOMMENDATIONS).toList();
    }

    /** Best YouTube video: official trailer > trailer > teaser. */
    Trailer trailer(TmdbModels.Videos videos) {
        if (videos == null || videos.results() == null) {
            return null;
        }
        List<Video> youtube = videos.results().stream()
                .filter(v -> "YouTube".equalsIgnoreCase(v.site()) && StringUtils.hasText(v.key()))
                .toList();
        Function<Video, Boolean> officialTrailer = v -> "Trailer".equals(v.type()) && Boolean.TRUE.equals(v.official());
        return Stream.<Function<Video, Boolean>>of(officialTrailer, v -> "Trailer".equals(v.type()), v -> "Teaser".equals(v.type()))
                .map(rule -> youtube.stream().filter(rule::apply).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .map(v -> new Trailer(v.key(), v.name(),
                        "https://www.youtube.com/watch?v=" + v.key(),
                        "https://www.youtube-nocookie.com/embed/" + v.key()))
                .orElse(null);
    }

    public static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
