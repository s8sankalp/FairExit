package com.sankalp.fairexit.exception;

import java.time.Instant;

/** The one error shape every endpoint returns. */
public record ApiError(int status, String message, Instant timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now());
    }
}
