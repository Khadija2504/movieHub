package com.moviehub.common.exception;

/** Thrown when a request is syntactically valid but breaks a business rule. Mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
