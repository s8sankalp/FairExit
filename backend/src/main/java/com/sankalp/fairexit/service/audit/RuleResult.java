package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.Verdict;
import java.util.List;
import java.util.UUID;

/**
 * What one rule says about one line. ruleRef is "id@version", for example "R3@1".
 * evidenceIds are the vault documents the rule relied on.
 */
public record RuleResult(Verdict verdict, String ruleRef, String reason, List<UUID> evidenceIds) {

    public RuleResult {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    public RuleResult(Verdict verdict, String ruleRef, String reason) {
        this(verdict, ruleRef, reason, List.of());
    }
}
