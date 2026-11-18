package com.moviehub.common.exception;

/** Thrown when a request conflicts with current state (e.g. email already used, seat already booked). Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
