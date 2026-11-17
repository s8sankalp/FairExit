package com.sankalp.fairexit.model;

/** What a clause's quote was checked against. */
public enum QuoteSource {
    /** The PDF's own text layer: strong proof the quote is in the document. */
    TEXT_LAYER,
    /** The model's transcription of a photo or scan: weaker, so the tenant should check it. */
    TRANSCRIPTION
}
