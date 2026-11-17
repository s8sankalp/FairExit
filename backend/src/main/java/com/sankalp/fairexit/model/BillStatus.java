package com.sankalp.fairexit.model;

/**
 * EXTRACTING -> EXTRACTED -> CONFIRMED -> AUDITED
 *      \-> EXTRACTION_FAILED -> CONFIRMED (tenant types the lines by hand)
 * A bill typed by hand starts at CONFIRMED.
 */
public enum BillStatus {
    /** The AI is reading the pasted message (a background job). */
    EXTRACTING,
    /** The AI's lines are ready for the tenant to check. */
    EXTRACTED,
    /** The AI could not read it; the tenant can still type the lines. */
    EXTRACTION_FAILED,
    /** The tenant has confirmed the lines; ready to audit. */
    CONFIRMED,
    /** At least one audit has run; the lines are now frozen. */
    AUDITED
}
