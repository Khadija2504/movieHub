package com.moviehub.catalog.tmdb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw TMDB response shapes (only the fields we use). Never returned to the frontend:
 * {@link TmdbMapper} turns them into MovieHub DTOs.
 */
public final class TmdbModels {

    private TmdbModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page<T>(
            int page,
            List<T> results,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_results") int totalResults) {
    }

    /** A list item from discover / trending / search / recommendations (movie or tv). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaItem(
            int id,
            String title,
            String name,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            @JsonProperty("genre_ids") List<Integer> genreIds,
            @JsonProperty("media_type") String mediaType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(int id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenreList(List<Genre> genres) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CastMember(
            int id,
            String name,
            String character,
            @JsonProperty("profile_path") String profilePath,
            Integer order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CrewMember(int id, String name, String job, @JsonProperty("profile_path") String profilePath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credits(List<CastMember> cast, List<CrewMember> crew) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Video(String key, String name, String site, String type, Boolean official) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Videos(List<Video> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieDetails(
            int id,
            String title,
            String tagline,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("release_date") String releaseDate,
            Integer runtime,
            String status,
            @JsonProperty("original_language") String originalLanguage,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            List<Genre> genres,
            Credits credits,
            Videos videos,
            Page<MediaItem> recommendations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Creator(int id, String name, @JsonProperty("profile_path") String profilePath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonSummary(
            @JsonProperty("season_number") int seasonNumber,
            String name,
            String overview,
            @JsonProperty("episode_count") Integer episodeCount,
            @JsonProperty("air_date") String airDate,
            @JsonProperty("poster_path") String posterPath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeriesDetails(
            int id,
            String name,
            String tagline,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("last_air_date") String lastAirDate,
            String status,
            @JsonProperty("number_of_seasons") Integer numberOfSeasons,
            @JsonProperty("number_of_episodes") Integer numberOfEpisodes,
            @JsonProperty("episode_run_time") List<Integer> episodeRunTime,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            List<Genre> genres,
            @JsonProperty("created_by") List<Creator> createdBy,
            List<SeasonSummary> seasons,
            Credits credits,
            Videos videos,
            Page<MediaItem> recommendations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Episode(
            @JsonProperty("episode_number") int episodeNumber,
            String name,
            String overview,
            @JsonProperty("air_date") String airDate,
            Integer runtime,
            @JsonProperty("still_path") String stillPath,
            @JsonProperty("vote_average") Double voteAverage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(
            @JsonProperty("season_number") int seasonNumber,
            String name,
            String overview,
            @JsonProperty("air_date") String airDate,
            @JsonProperty("poster_path") String posterPath,
            List<Episode> episodes) {
    }
}
