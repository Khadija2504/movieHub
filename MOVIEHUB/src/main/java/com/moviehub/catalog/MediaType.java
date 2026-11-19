package com.moviehub.catalog;

public enum MediaType {
    MOVIE("movie"),
    SERIES("tv");

    private final String tmdbType;

    MediaType(String tmdbType) {
        this.tmdbType = tmdbType;
    }

    /** The path segment TMDB uses for this type ("movie" / "tv"). */
    public String tmdbType() {
        return tmdbType;
    }

    public static MediaType fromTmdb(String value) {
        return switch (value) {
            case "movie" -> MOVIE;
            case "tv" -> SERIES;
            default -> null;
        };
    }
}
