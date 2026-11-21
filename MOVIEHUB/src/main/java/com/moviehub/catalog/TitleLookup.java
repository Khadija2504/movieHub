package com.moviehub.catalog;

import org.springframework.stereotype.Service;

import com.moviehub.catalog.tmdb.TmdbClient;
import com.moviehub.catalog.tmdb.TmdbMapper;
import com.moviehub.catalog.tmdb.TmdbModels;

/**
 * Resolves the display data of a title (name, poster, year) from TMDB (cached).
 * Used when saving watchlist items and reviews, so the client never decides what title it is saving.
 */
@Service
public class TitleLookup {

    private final TmdbClient tmdb;

    public TitleLookup(TmdbClient tmdb) {
        this.tmdb = tmdb;
    }

    public record TitleSnapshot(MediaType mediaType, int tmdbId, String title, String posterPath, Integer year) {
    }

    /** @throws com.moviehub.common.exception.ResourceNotFoundException if TMDB does not know the title */
    public TitleSnapshot lookup(MediaType type, int tmdbId) {
        if (type == MediaType.MOVIE) {
            TmdbModels.MovieDetails m = tmdb.movie(tmdbId);
            return new TitleSnapshot(type, tmdbId, m.title(), m.posterPath(), year(m.releaseDate()));
        }
        TmdbModels.SeriesDetails s = tmdb.series(tmdbId);
        return new TitleSnapshot(type, tmdbId, s.name(), s.posterPath(), year(s.firstAirDate()));
    }

    private static Integer year(String date) {
        var parsed = TmdbMapper.parseDate(date);
        return parsed == null ? null : parsed.getYear();
    }
}
