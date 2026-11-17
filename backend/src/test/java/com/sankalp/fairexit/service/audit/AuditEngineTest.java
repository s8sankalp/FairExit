package com.sankalp.fairexit.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.model.Verdict;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEngineTest {

    private final AuditEngine engine = new AuditEngine();
    private final LineInput line = new LineInput(UUID.randomUUID(), "Item", DeductionCategory.OTHER,
            new BigDecimal("100"), null);

    /** A rule that always answers with the given verdict, for testing the engine alone. */
    private static AuditRule always(String ref, Verdict verdict) {
        return new AuditRule() {
            @Override
            public String ref() {
                return ref;
            }

            @Override
            public Optional<RuleResult> evaluate(LineInput l, AuditContext c) {
                return Optional.of(new RuleResult(verdict, ref, verdict + " because " + ref));
            }
        };
    }

    private static AuditRule silent(String ref) {
        return new AuditRule() {
            @Override
            public String ref() {
                return ref;
            }

            @Override
            public Optional<RuleResult> evaluate(LineInput l, AuditContext c) {
                return Optional.empty();
            }
        };
    }

    private static RuleSet rules(AuditRule... checks) {
        return new RuleSet(List.of(checks), always("R7@1", Verdict.NEEDS_INFO), RulesCoverage.FULL, "test");
    }

    @Test
    void strongestVerdictWinsAndEveryFiredRuleIsListed() {
        AuditOutcome.LineVerdict v = engine.judge(line, WorkedExample.context(),
                rules(always("A@1", Verdict.FAIR), always("B@1", Verdict.NOT_IN_AGREEMENT),
                        always("C@1", Verdict.QUESTIONABLE)));

        assertThat(v.verdict()).isEqualTo(Verdict.NOT_IN_AGREEMENT);
        assertThat(v.ruleRefs()).containsExactly("A@1", "B@1", "C@1");
        assertThat(v.reason()).isEqualTo("NOT_IN_AGREEMENT because B@1");
    }

    @Test
    void questionableBeatsFair() {
        AuditOutcome.LineVerdict v = engine.judge(line, WorkedExample.context(),
                rules(always("A@1", Verdict.FAIR), always("B@1", Verdict.QUESTIONABLE)));

        assertThat(v.verdict()).isEqualTo(Verdict.QUESTIONABLE);
    }

    @Test
    void fallbackRunsOnlyWhenNothingFires() {
        AuditOutcome.LineVerdict v = engine.judge(line, WorkedExample.context(), rules(silent("A@1")));

        assertThat(v.verdict()).isEqualTo(Verdict.NEEDS_INFO);
        assertThat(v.ruleRefs()).containsExactly("R7@1");
    }

    @Test
    void reasonsOfTiedWinnersAreJoined() {
        AuditOutcome.LineVerdict v = engine.judge(line, WorkedExample.context(),
                rules(always("A@1", Verdict.QUESTIONABLE), always("B@1", Verdict.QUESTIONABLE)));

        assertThat(v.reason()).isEqualTo("QUESTIONABLE because A@1 QUESTIONABLE because B@1");
    }

    @Test
    void ruleSetOrdersByPriorityAndRecordsTheVersion() {
        RuleSet set = RuleSet.of(List.of(TestRules.r7(), TestRules.r3(), TestRules.r1(), TestRules.r2()),
                RulesCoverage.FULL);

        assertThat(set.checks()).extracting(AuditRule::ref).containsExactly("R1@1", "R2@1", "R3@1");
        assertThat(set.fallback().ref()).isEqualTo("R7@1");
        assertThat(set.version()).isEqualTo("R1@1,R2@1,R3@1,R7@1");
    }

    @Test
    void unknownRuleTypeFailsFast() {
        RuleSpec bad = new RuleSpec("R9", 1, "MAGIC", 5, Map.of(), "x");

        assertThatThrownBy(() -> RuleSet.of(List.of(bad, TestRules.r7()), RulesCoverage.FULL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAGIC");
    }

    @Test
    void ruleSetWithoutFallbackFailsFast() {
        assertThatThrownBy(() -> RuleSet.of(List.of(TestRules.r1()), RulesCoverage.FULL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallback");
    }
}
