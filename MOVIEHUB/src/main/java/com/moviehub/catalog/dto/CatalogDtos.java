package com.moviehub.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.moviehub.catalog.MediaType;

/**
 * Public catalog API shapes. {@code id} is always the TMDB id: it is what the frontend uses in URLs
 * (/movies/{id}, /series/{id}).
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** A card in a list or carousel. */
    public record MediaSummary(
            int id,
            MediaType mediaType,
            String title,
            String overview,
            String posterUrl,
            String backdropUrl,
            LocalDate releaseDate,
            Integer year,
            Double rating,
            Integer voteCount,
            List<Integer> genreIds) {
    }

    public record Genre(int id, String name) {
    }

    public record Person(int id, String name, String role, String profileUrl) {
    }

    public record Trailer(String key, String name, String watchUrl, String embedUrl) {
    }

    /**
     * What MovieHub offers for a movie. {@code inCatalog=false} means the title has not been imported.
     *
     * @param streamable      it can be watched on MovieHub (it has a video)
     * @param inSubscription  subscribers watch it at no extra cost (otherwise: buy or rent only)
     */
    public record MovieOffer(boolean inCatalog, boolean streamable, boolean inSubscription, BigDecimal purchasePrice,
            BigDecimal rentalPrice) {

        public static final MovieOffer NONE = new MovieOffer(false, false, false, null, null);
    }

    public record SeriesOffer(boolean inCatalog, boolean streamable) {

        public static final SeriesOffer NONE = new SeriesOffer(false, false);
    }

    public record MovieDetails(
            int id,
            String title,
            String tagline,
            String overview,
            String posterUrl,
            String backdropUrl,
            LocalDate releaseDate,
            Integer year,
            Integer runtime,
            String status,
            String originalLanguage,
            Double rating,
            Integer voteCount,
            List<Genre> genres,
            List<Person> directors,
            List<Person> cast,
            Trailer trailer,
            List<MediaSummary> recommendations,
            MovieOffer offer) {
    }

    public record SeasonSummary(
            int seasonNumber,
            String name,
            String overview,
            Integer episodeCount,
            LocalDate airDate,
            String posterUrl) {
    }

    public record SeriesDetails(
            int id,
            String title,
            String tagline,
            String overview,
            String posterUrl,
            String backdropUrl,
            LocalDate firstAirDate,
            LocalDate lastAirDate,
            Integer year,
            String status,
            Integer numberOfSeasons,
            Integer numberOfEpisodes,
            Integer episodeRuntime,
            Double rating,
            Integer voteCount,
            List<Genre> genres,
            List<Person> creators,
            List<Person> cast,
            Trailer trailer,
            List<SeasonSummary> seasons,
            List<MediaSummary> recommendations,
            SeriesOffer offer) {
    }

    public record Episode(
            int episodeNumber,
            String name,
            String overview,
            LocalDate airDate,
            Integer runtime,
            String stillUrl,
            Double rating) {
    }

    public record SeasonDetails(
            int seriesId,
            int seasonNumber,
            String name,
            String overview,
            LocalDate airDate,
            String posterUrl,
            List<Episode> episodes) {
    }
}
