package com.sankalp.fairexit.model;

public enum PriceSource {
    /** Quotes collected from service providers and imported by an admin. */
    COLLECTED,
    /** What a tenant was actually charged (from outcomes, module 8). */
    USER
}
