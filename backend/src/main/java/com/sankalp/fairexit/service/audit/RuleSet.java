package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.RulesCoverage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rules that apply to one state, in evaluation order, plus the fallback.
 * version lists every rule used, for example "R1@1,R2@1,R3@2,R4@1,R5@1,R6@1,R7@1", and is stored
 * with each audit so an old result can be explained exactly.
 */
public record RuleSet(List<AuditRule> checks, AuditRule fallback, RulesCoverage coverage, String version) {

    static final String FALLBACK_TYPE = "NEEDS_INFO";

    /** rule_type in the table decides which Java class runs the rule. */
    private static final Map<String, Function<RuleSpec, AuditRule>> FACTORIES = Map.of(
            "DUES_MATCH", DuesMatchRule::new,
            "AGREEMENT_CHECK", AgreementCheckRule::new,
            "WEAR_AND_TEAR", WearAndTearRule::new,
            "PRESENT_AT_MOVE_IN", PresentAtMoveInRule::new,
            "PRICE_CHECK", PriceCheckRule::new,
            "DAMAGE_WITH_EVIDENCE", DamageWithEvidenceRule::new,
            FALLBACK_TYPE, NeedsInfoRule::new);

    public RuleSet {
        checks = List.copyOf(checks);
    }

    /** Builds a rule set from specs. Fails fast on an unknown rule type or a missing fallback. */
    public static RuleSet of(List<RuleSpec> specs, RulesCoverage coverage) {
        List<RuleSpec> ordered = specs.stream()
                .sorted(Comparator.comparingInt(RuleSpec::priority).thenComparing(RuleSpec::ruleId))
                .toList();

        AuditRule fallback = null;
        List<AuditRule> checks = new java.util.ArrayList<>();
        for (RuleSpec spec : ordered) {
            Function<RuleSpec, AuditRule> factory = FACTORIES.get(spec.ruleType());
            if (factory == null) {
                throw new IllegalStateException("Unknown rule type " + spec.ruleType() + " for " + spec.ref());
            }
            AuditRule rule = factory.apply(spec);
            if (FALLBACK_TYPE.equals(spec.ruleType())) {
                fallback = rule;
            } else {
                checks.add(rule);
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("A rule set needs a " + FALLBACK_TYPE + " fallback rule");
        }
        String version = ordered.stream().map(RuleSpec::ref).collect(Collectors.joining(","));
        return new RuleSet(checks, fallback, coverage, version);
    }
}
