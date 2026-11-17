package com.sankalp.fairexit.config;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** What Redis keeps for one Idempotency-Key: first a claim, then the finished response. */
public record IdempotencyRecord(String state, String fingerprint, int status, String contentType,
                                String location, String body) {

    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String COMPLETED = "COMPLETED";

    static IdempotencyRecord inProgress(String fingerprint) {
        return new IdempotencyRecord(IN_PROGRESS, fingerprint, 0, null, null, null);
    }

    static IdempotencyRecord completed(String fingerprint, int status, String contentType,
                                       String location, String body) {
        return new IdempotencyRecord(COMPLETED, fingerprint, status, contentType, location, body);
    }

    @JsonIgnore // derived from state, not stored
    boolean isInProgress() {
        return IN_PROGRESS.equals(state);
    }
}
