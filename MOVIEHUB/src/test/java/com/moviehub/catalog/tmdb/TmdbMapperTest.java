package com.moviehub.catalog.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moviehub.catalog.MediaType;
import com.moviehub.catalog.dto.CatalogDtos.MovieOffer;
import com.moviehub.catalog.tmdb.TmdbModels.CastMember;
import com.moviehub.catalog.tmdb.TmdbModels.Credits;
import com.moviehub.catalog.tmdb.TmdbModels.CrewMember;
import com.moviehub.catalog.tmdb.TmdbModels.MediaItem;
import com.moviehub.catalog.tmdb.TmdbModels.Page;
import com.moviehub.catalog.tmdb.TmdbModels.Video;
import com.moviehub.catalog.tmdb.TmdbModels.Videos;
import com.moviehub.config.AppProperties;

class TmdbMapperTest {

    private final TmdbMapper mapper = new TmdbMapper(new AppProperties(null,
            new AppProperties.Tmdb("k", "https://api", "https://img", "en-US", Duration.ZERO, Duration.ZERO, null), null, null));

    private static MediaItem item(int id, String mediaType, String title, String name, String date) {
        return new MediaItem(id, title, name, "overview", "/p.jpg", null, date, date, 7.456, 100, List.of(28), mediaType);
    }

    @Test
    void mapsMoviesAndSeriesAndDropsPeople() {
        var page = new Page<>(1, List.of(
                item(1, "movie", "Movie", null, "2020-05-01"),
                item(2, "tv", null, "Show", "2019-01-01"),
                item(3, "person", null, "Someone", null)), 900, 3);

        var result = mapper.toPage(page, null);

        assertThat(result.content()).extracting("mediaType").containsExactly(MediaType.MOVIE, MediaType.SERIES);
        assertThat(result.content()).extracting("title").containsExactly("Movie", "Show");
        assertThat(result.totalPages()).isEqualTo(TmdbMapper.MAX_PAGE);

        var movie = result.content().get(0);
        assertThat(movie.posterUrl()).isEqualTo("https://img/w500/p.jpg");
        assertThat(movie.backdropUrl()).isNull();
        assertThat(movie.year()).isEqualTo(2020);
        assertThat(movie.rating()).isEqualTo(7.5);
    }

    @Test
    void toleratesEmptyOrInvalidDates() {
        assertThat(TmdbMapper.parseDate("")).isNull();
        assertThat(TmdbMapper.parseDate("2024-13-45")).isNull();
        assertThat(TmdbMapper.parseDate("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void prefersOfficialYoutubeTrailer() {
        var videos = new Videos(List.of(
                new Video("t1", "Teaser", "YouTube", "Teaser", true),
                new Video("v1", "Vimeo trailer", "Vimeo", "Trailer", true),
                new Video("t2", "Fan trailer", "YouTube", "Trailer", false),
                new Video("t3", "Official Trailer", "YouTube", "Trailer", true)));

        var trailer = mapper.trailer(videos);

        assertThat(trailer.key()).isEqualTo("t3");
        assertThat(trailer.embedUrl()).isEqualTo("https://www.youtube-nocookie.com/embed/t3");
    }

    @Test
    void mapsMovieDetailsWithCastOrderAndDirectors() {
        var credits = new Credits(
                List.of(new CastMember(10, "Second", "B", null, 1), new CastMember(11, "First", "A", "/a.jpg", 0)),
                List.of(new CrewMember(20, "Director Name", "Director", null), new CrewMember(21, "Writer", "Screenplay", null)));
        var details = new TmdbModels.MovieDetails(550, "Fight Club", "", "o", null, null, "1999-10-15", 139,
                "Released", "en", 8.4, 1000, List.of(new TmdbModels.Genre(18, "Drama")), credits, null, null);

        var dto = mapper.toMovieDetails(details, null);

        assertThat(dto.tagline()).isNull();
        assertThat(dto.cast()).extracting("name").containsExactly("First", "Second");
        assertThat(dto.cast().get(0).profileUrl()).isEqualTo("https://img/w185/a.jpg");
        assertThat(dto.directors()).extracting("name").containsExactly("Director Name");
        assertThat(dto.genres()).extracting("name").containsExactly("Drama");
        assertThat(dto.offer()).isEqualTo(MovieOffer.NONE);
        assertThat(dto.recommendations()).isEmpty();
    }
}
