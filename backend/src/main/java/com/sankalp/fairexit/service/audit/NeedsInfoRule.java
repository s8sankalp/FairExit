package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.Verdict;
import java.util.Map;
import java.util.Optional;

/**
 * Rule 7, the fallback. When no other rule can decide, say so honestly and give the
 * tenant the exact question to ask the landlord. It never guesses.
 */
public class NeedsInfoRule implements AuditRule {

    private static final String DEFAULT_QUESTION = "Ask for an invoice or proof for this charge.";

    private final RuleSpec spec;
    private final Map<String, String> questions;

    public NeedsInfoRule(RuleSpec spec) {
        this.spec = spec;
        this.questions = spec.mapParam("questions");
    }

    @Override
    public String ref() {
        return spec.ref();
    }

    @Override
    public Optional<RuleResult> evaluate(LineInput line, AuditContext context) {
        String question = questions.getOrDefault(line.category().name(), DEFAULT_QUESTION);
        return Optional.of(new RuleResult(Verdict.NEEDS_INFO, ref(), spec.reason(Map.of("question", question))));
    }
}
