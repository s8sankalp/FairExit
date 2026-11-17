package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import com.sankalp.fairexit.model.Verdict;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule 3. Routine painting or normal cleaning after a long enough stay (11 months by
 * default) is usually normal wear and tear, unless the agreement makes the tenant pay.
 *
 * Version 2 adds skipWhenDamageShown: when the tenant's own move out photos show real
 * damage, the charge is not routine, so this rule stays silent and rule 6 can judge it.
 */
public class WearAndTearRule implements AuditRule {

    private final RuleSpec spec;
    private final Set<DeductionCategory> categories;
    private final int minTenancyMonths;
    private final boolean skipWhenDamageShown;

    public WearAndTearRule(RuleSpec spec) {
        this.spec = spec;
        this.categories = spec.listParam("categories").stream()
                .map(DeductionCategory::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        this.minTenancyMonths = spec.intParam("minTenancyMonths", 11);
        this.skipWhenDamageShown = Boolean.TRUE.equals(spec.parameters().get("skipWhenDamageShown"));
    }

    @Override
    public String ref() {
        return spec.ref();
    }

    @Override
    public Optional<RuleResult> evaluate(LineInput line, AuditContext context) {
        boolean tenantAgreedToPay = context.agreementKnown()
                && context.agreement().makesTenantPay(line.category());
        if (!categories.contains(line.category())
                || context.tenancyMonths() < minTenancyMonths
                || tenantAgreedToPay
                || (skipWhenDamageShown
                    && !line.photos(DocumentPhase.MOVE_OUT, EvidenceObservation.DAMAGE_PRESENT).isEmpty())) {
            return Optional.empty();
        }
        String reason = spec.reason(Map.of(
                "item", line.item(),
                "months", String.valueOf(context.tenancyMonths())));
        if (!context.agreementKnown()) {
            reason += " Upload your agreement to confirm.";
        }
        return Optional.of(new RuleResult(Verdict.QUESTIONABLE, ref(), reason));
    }
}
