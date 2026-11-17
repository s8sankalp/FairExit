package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.Verdict;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule 2. Some charges (society charges like lift usage, unexplained fees) are only
 * allowed when the agreement says so. Damage, rent and utilities are legal dues
 * even without a clause, so they are not listed here.
 *
 * Only runs when the agreement is known: no agreement on file means "we cannot tell",
 * never "not allowed".
 */
public class AgreementCheckRule implements AuditRule {

    private final RuleSpec spec;
    private final Set<DeductionCategory> requiresClause;

    public AgreementCheckRule(RuleSpec spec) {
        this.spec = spec;
        this.requiresClause = spec.listParam("requiresClause").stream()
                .map(DeductionCategory::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String ref() {
        return spec.ref();
    }

    @Override
    public Optional<RuleResult> evaluate(LineInput line, AuditContext context) {
        if (!context.agreementKnown()
                || !requiresClause.contains(line.category())
                || context.agreement().makesTenantPay(line.category())) {
            return Optional.empty();
        }
        String reason = spec.reason(Map.of("item", line.item(), "category", line.category().label()));
        return Optional.of(new RuleResult(Verdict.NOT_IN_AGREEMENT, ref(), reason));
    }
}
