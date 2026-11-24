package com.moviehub.common.exception;

/** The user is known but not allowed to do this (e.g. watch a movie they have no access to). Mapped to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
