package com.sankalp.fairexit.model;

/**
 * Result for one bill line. When several rules fire, the strongest verdict wins:
 * NOT_IN_AGREEMENT beats QUESTIONABLE beats FAIR. NEEDS_INFO is only the fallback
 * when no rule can decide.
 */
public enum Verdict {
    NEEDS_INFO(0),
    FAIR(1),
    QUESTIONABLE(2),
    NOT_IN_AGREEMENT(3);

    private final int strength;

    Verdict(int strength) {
        this.strength = strength;
    }

    public int strength() {
        return strength;
    }

    /** Money the tenant disputes: questionable or not allowed by the agreement. */
    public boolean isDisputed() {
        return this == QUESTIONABLE || this == NOT_IN_AGREEMENT;
    }
}
