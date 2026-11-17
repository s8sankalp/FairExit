package com.sankalp.fairexit.model;

public enum BillSource {
    /** Lines typed by the tenant. */
    MANUAL,
    /** Pasted message read by the AI (module 4). */
    TEXT,
    /** Photo or PDF read by the AI (module 6). */
    FILE
}
