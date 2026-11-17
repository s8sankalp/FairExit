package com.sankalp.fairexit.config;

import java.util.UUID;

/** The logged in user, read from the JWT. Controllers get it via @AuthenticationPrincipal. */
public record AuthenticatedUser(UUID id, String email, boolean admin) {

    public AuthenticatedUser(UUID id, String email) {
        this(id, email, false);
    }
}
