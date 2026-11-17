package com.sankalp.fairexit.model;

/** The kinds of charges a landlord puts on a deduction bill (from the PRD). */
public enum DeductionCategory {
    PAINTING("painting"),
    CLEANING("cleaning"),
    REPAIR("repairs"),
    UTILITY("utility bills"),
    RENT("rent"),
    SOCIETY_CHARGE("society charges"),
    OTHER("other charges");

    private final String label;

    DeductionCategory(String label) {
        this.label = label;
    }

    /** Lower case words for use inside a sentence. */
    public String label() {
        return label;
    }
}
