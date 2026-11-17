package com.sankalp.fairexit.model;

/** How much the audit can say for a tenancy's state. */
public enum RulesCoverage {
    /** State specific rules exist (Karnataka, Kerala). */
    FULL,
    /** No state rules yet: verdicts are labelled general guidance. */
    GENERAL_GUIDANCE
}
