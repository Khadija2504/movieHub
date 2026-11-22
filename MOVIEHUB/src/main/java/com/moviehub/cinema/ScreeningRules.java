package com.moviehub.cinema;

import java.time.Duration;

/** Scheduling constants shared by the public and admin services. */
public final class ScreeningRules {

    /** Time to clean the room between two screenings. */
    public static final Duration CLEANING_TIME = Duration.ofMinutes(15);

    /** Used when TMDB does not know a movie's runtime. */
    public static final int DEFAULT_RUNTIME_MINUTES = 120;

    /** How far ahead showtimes are listed on public pages. */
    public static final int SCHEDULE_DAYS = 14;

    private ScreeningRules() {
    }

    /** How long a room is busy for a movie: runtime + cleaning. */
    public static Duration slot(Integer runtimeMinutes) {
        int runtime = runtimeMinutes != null && runtimeMinutes > 0 ? runtimeMinutes : DEFAULT_RUNTIME_MINUTES;
        return Duration.ofMinutes(runtime).plus(CLEANING_TIME);
    }
}
