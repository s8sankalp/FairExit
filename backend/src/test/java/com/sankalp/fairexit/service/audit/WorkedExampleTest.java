package com.sankalp.fairexit.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.model.Verdict;
import com.sankalp.fairexit.service.audit.AuditOutcome.LineVerdict;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The research report's worked example, with all seven rules. This is the report's
 * full answer: ₹65,000 of ₹69,700 questionable, agree to ₹4,700, refund ₹1,45,300.
 */
class WorkedExampleTest {

    private AuditOutcome outcome;
    private Map<String, LineVerdict> byItem;

    @BeforeEach
    void runAudit() {
        outcome = new AuditEngine().run(WorkedExample.lines(), WorkedExample.context(), TestRules.defaults());
        byItem = outcome.verdicts().stream()
                .collect(Collectors.toMap(v -> v.line().item(), Function.identity()));
    }

    @Test
    void repaintingAfterTwoYearsIsQuestionableAsWearAndTear() {
        LineVerdict v = byItem.get("Full flat repainting");
        assertThat(v.verdict()).isEqualTo(Verdict.QUESTIONABLE);
        assertThat(v.ruleRefs()).containsExactly("R3@2"); // no painting prices yet, so R5 is silent
        assertThat(v.reason()).contains("after 24 months").contains("normal wear and tear")
                .doesNotContain("Upload your agreement");
    }

    @Test
    void liftUsageIsNotInTheAgreement() {
        LineVerdict v = byItem.get("Lift usage");
        assertThat(v.verdict()).isEqualTo(Verdict.NOT_IN_AGREEMENT);
        assertThat(v.ruleRefs()).containsExactly("R2@1");
    }

    @Test
    void deepCleaningIsQuestionableBecauseItIsAboveTheUsualPrice() {
        LineVerdict v = byItem.get("Deep cleaning");
        assertThat(v.verdict()).isEqualTo(Verdict.QUESTIONABLE);
        assertThat(v.ruleRefs()).containsExactly("R5@1");
        assertThat(v.reason()).isEqualTo("\"Deep cleaning\" at ₹8,000 is above the usual range: 9 in 10 of 12 known"
                + " prices for cleaning for a 2BHK in Bengaluru are ₹6,000 or less.");
    }

    @Test
    void theBrokenTapIsFairWithBothPhotosAsEvidence() {
        LineVerdict v = byItem.get("Broken bathroom tap");
        assertThat(v.verdict()).isEqualTo(Verdict.FAIR);
        assertThat(v.ruleRefs()).containsExactly("R6@1");
        assertThat(v.evidenceIds())
                .containsExactly(WorkedExample.TAP_MOVE_IN_PHOTO, WorkedExample.TAP_MOVE_OUT_PHOTO);
    }

    @Test
    void electricityMatchingTheBillIsFair() {
        LineVerdict v = byItem.get("Pending electricity bill");
        assertThat(v.verdict()).isEqualTo(Verdict.FAIR);
        assertThat(v.reason()).isEqualTo("Matches the utility bill of ₹3,200 on file.");
    }

    @Test
    void totalsMatchTheResearchReport() {
        AuditOutcome.Totals t = outcome.totals();
        assertThat(t.claimed()).isEqualByComparingTo("69700");
        assertThat(t.questionable()).isEqualByComparingTo("65000"); // "₹65,000 of ₹69,700 is questionable"
        assertThat(t.fair()).isEqualByComparingTo("4700");          // "I agree to ... ₹4,700"
        assertThat(t.needsInfo()).isEqualByComparingTo("0");
        assertThat(t.suggestedRefund()).isEqualByComparingTo("145300"); // "Please return ₹1,45,300"
    }

    @Test
    void withOnlyTheModule3RulesTheSameBillLeavesTwoLinesOpen() {
        AuditOutcome m3 = new AuditEngine().run(WorkedExample.lines(), WorkedExample.context(), TestRules.module3());

        assertThat(m3.verdicts()).extracting(LineVerdict::verdict).containsExactly(
                Verdict.QUESTIONABLE, Verdict.NOT_IN_AGREEMENT, Verdict.NEEDS_INFO, Verdict.NEEDS_INFO, Verdict.FAIR);
        assertThat(m3.totals().suggestedRefund()).isEqualByComparingTo("146800");
    }

    @Test
    void sameInputAlwaysGivesTheSameOutput() {
        AuditOutcome again = new AuditEngine().run(WorkedExample.lines(), WorkedExample.context(), TestRules.defaults());
        assertThat(again).isEqualTo(outcome);
    }

    @Test
    void ruleSetVersionIsRecorded() {
        assertThat(TestRules.defaults().version()).isEqualTo("R1@1,R2@1,R3@2,R4@1,R5@1,R6@1,R7@1");
    }

    @Test
    void refundNeverGoesBelowZero() {
        AuditContext c = WorkedExample.context();
        AuditContext tinyDeposit = new AuditContext(c.state(), c.flatSize(), c.city(), c.rent(), new BigDecimal("1000"),
                c.tenancyMonths(), c.agreement(), c.knownBills(), c.prices());

        AuditOutcome o = new AuditEngine().run(WorkedExample.lines(), tinyDeposit, TestRules.defaults());

        assertThat(o.totals().suggestedRefund()).isEqualByComparingTo("0");
    }
}
