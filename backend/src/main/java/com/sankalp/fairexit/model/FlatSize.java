package com.sankalp.fairexit.model;

/** Used to compare repair prices between similar homes. */
public enum FlatSize {
    PG_ROOM("PG room"),
    ONE_RK("1RK"),
    ONE_BHK("1BHK"),
    TWO_BHK("2BHK"),
    THREE_BHK("3BHK"),
    FOUR_BHK_PLUS("4BHK or bigger");

    private final String label;

    FlatSize(String label) {
        this.label = label;
    }

    /** How people in India write it, for use in a sentence. */
    public String label() {
        return label;
    }
}
