package com.sankalp.fairexit.model;

public enum DocumentStatus {
    /** Upload URL handed out, file not confirmed yet. Not part of the chain. */
    PENDING,
    /** Hashed and chained. Never changes again. */
    STORED,
    /** The uploaded file was not what was declared (wrong type or size). */
    REJECTED
}
