package com.sankalp.fairexit.service.audit;

import java.util.Optional;

/**
 * One check. Returns empty when the rule has nothing to say about the line.
 * Implementations must be pure: no clock, no database, no randomness,
 * so the same input always gives the same verdict.
 */
public interface AuditRule {

    String ref();

    Optional<RuleResult> evaluate(LineInput line, AuditContext context);
}
