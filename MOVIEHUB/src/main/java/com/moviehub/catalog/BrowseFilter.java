package com.moviehub.catalog;

import com.moviehub.catalog.tmdb.TmdbMapper;

/**
 * Filters of the Movies / TV Series browse pages. All fields are optional.
 *
 * @param minRating 0-10
 * @param page      1-based, clamped to TMDB's 1..500
 */
public record BrowseFilter(Integer genre, Integer year, Double minRating, SortOption sort, Integer page) {

    public SortOption sortOrDefault() {
        return sort == null ? SortOption.POPULAR : sort;
    }

    public int pageOrDefault() {
        return page == null ? 1 : Math.max(1, Math.min(page, TmdbMapper.MAX_PAGE));
    }
}
