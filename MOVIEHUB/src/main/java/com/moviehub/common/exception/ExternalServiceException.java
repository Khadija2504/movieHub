package com.moviehub.common.exception;

/** A third-party service (TMDB, payment provider, ...) failed or is not configured. Mapped to 502 / 503. */
public class ExternalServiceException extends RuntimeException {

    private final boolean unavailable;

    private ExternalServiceException(String message, boolean unavailable, Throwable cause) {
        super(message, cause);
        this.unavailable = unavailable;
    }

    /** The service answered with an error or could not be reached (502). */
    public static ExternalServiceException failed(String message, Throwable cause) {
        return new ExternalServiceException(message, false, cause);
    }

    /** The service is not configured / temporarily unavailable (503). */
    public static ExternalServiceException unavailable(String message) {
        return new ExternalServiceException(message, true, null);
    }

    public boolean isUnavailable() {
        return unavailable;
    }
}
