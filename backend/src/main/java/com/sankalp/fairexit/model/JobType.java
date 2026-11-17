package com.sankalp.fairexit.model;

public enum JobType {
    /** Read a deduction bill (pasted text, photo or PDF) into bill lines. */
    EXTRACT_BILL,
    /** Read the clauses of a rent agreement. */
    EXTRACT_CLAUSES,
    /** Remove a deleted account's files from object storage. */
    DELETE_OBJECTS
}
