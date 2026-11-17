package com.sankalp.fairexit.model;

/**
 * What a clause is about. Charge clauses (painting, cleaning, ...) map to a deduction
 * category: when the clause says the tenant pays, audit rules 2 and 3 respect it.
 */
public enum ClauseType {
    DEPOSIT(null),
    RENT(null),
    NOTICE_PERIOD(null),
    REFUND_TIMELINE(null),
    LOCK_IN(null),
    PAINTING(DeductionCategory.PAINTING),
    CLEANING(DeductionCategory.CLEANING),
    REPAIRS(DeductionCategory.REPAIR),
    SOCIETY_CHARGES(DeductionCategory.SOCIETY_CHARGE),
    OTHER_CHARGES(DeductionCategory.OTHER);

    private final DeductionCategory chargeCategory;

    ClauseType(DeductionCategory chargeCategory) {
        this.chargeCategory = chargeCategory;
    }

    /** The deduction category this clause decides, or null for clauses that are not about charges. */
    public DeductionCategory chargeCategory() {
        return chargeCategory;
    }
}
