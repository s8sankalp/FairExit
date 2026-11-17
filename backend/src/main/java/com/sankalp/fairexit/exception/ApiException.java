package com.sankalp.fairexit.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for errors we throw on purpose (not found, conflict, forbidden ...).
 * The global handler turns it into an ApiError with the given status.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
