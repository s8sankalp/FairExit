package com.sankalp.fairexit.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.model.Verdict;
import com.sankalp.fairexit.service.audit.AuditContext.AgreementTerms;
import com.sankalp.fairexit.service.audit.AuditContext.KnownBill;
import com.sankalp.fairexit.service.audit.AuditContext.PriceStat;
import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Fixed examples for every rule, one nested class per rule. */
class RulesTest {

    private static final BigDecimal RENT = new BigDecimal("25000");

    private static LineInput line(DeductionCategory category, String amount) {
        return new LineInput(UUID.randomUUID(), "Item", category, new BigDecimal(amount), null);
    }

    private static AuditContext context(int months, AgreementTerms agreement, List<KnownBill> bills) {
        return new AuditContext(IndianState.KA, FlatSize.ONE_BHK, "Bengaluru", RENT,
                new BigDecimal("100000"), months, agreement, bills);
    }

    private static AgreementTerms agreementCovering(DeductionCategory... categories) {
        return new AgreementTerms(Set.of(categories));
    }

    @Nested
    class R1DuesMatch {

        private final AuditRule rule = new DuesMatchRule(TestRules.r1());

        @ParameterizedTest
        @CsvSource({"3200", "3199", "3201"}) // within the ₹1 tolerance
        void utilityMatchingAKnownBillIsFair(String amount) {
            AuditContext ctx = context(12, null, List.of(new KnownBill(DeductionCategory.UTILITY, new BigDecimal("3200"))));

            Optional<RuleResult> r = rule.evaluate(line(DeductionCategory.UTILITY, amount), ctx);

            assertThat(r).map(RuleResult::verdict).contains(Verdict.FAIR);
        }

        @Test
        void utilityThatMatchesNoBillSaysNothing() {
            AuditContext ctx = context(12, null, List.of(new KnownBill(DeductionCategory.UTILITY, new BigDecimal("3200"))));

            assertThat(rule.evaluate(line(DeductionCategory.UTILITY, "4500"), ctx)).isEmpty();
        }

        @Test
        void utilityWithNoBillOnFileSaysNothing() {
            assertThat(rule.evaluate(line(DeductionCategory.UTILITY, "3200"), context(12, null, List.of()))).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({"25000, 1 month", "50000, 2 months", "75000, 3 months"})
        void wholeMonthsOfRentAreFair(String amount, String expected) {
            Optional<RuleResult> r = rule.evaluate(line(DeductionCategory.RENT, amount), context(12, null, List.of()));

            assertThat(r).map(RuleResult::verdict).contains(Verdict.FAIR);
            assertThat(r.get().reason()).contains(expected).contains("₹25,000 a month");
        }

        @ParameterizedTest
        @CsvSource({"12500", "100000"}) // half a month; more than 3 months
        void otherRentAmountsSayNothing(String amount) {
            assertThat(rule.evaluate(line(DeductionCategory.RENT, amount), context(12, null, List.of()))).isEmpty();
        }

        @Test
        void otherCategoriesAreIgnored() {
            assertThat(rule.evaluate(line(DeductionCategory.PAINTING, "25000"), context(12, null, List.of()))).isEmpty();
        }
    }

    @Nested
    class R2AgreementCheck {

        private final AuditRule rule = new AgreementCheckRule(TestRules.r2());

        @ParameterizedTest
        @EnumSource(value = DeductionCategory.class, names = {"SOCIETY_CHARGE", "OTHER"})
        void chargeNeedingAClauseWithoutOneIsNotInAgreement(DeductionCategory category) {
            Optional<RuleResult> r = rule.evaluate(line(category, "2000"), context(12, agreementCovering(), List.of()));

            assertThat(r).map(RuleResult::verdict).contains(Verdict.NOT_IN_AGREEMENT);
        }

        @Test
        void aClauseAllowingTheChargeSilencesTheRule() {
            AuditContext ctx = context(12, agreementCovering(DeductionCategory.SOCIETY_CHARGE), List.of());

            assertThat(rule.evaluate(line(DeductionCategory.SOCIETY_CHARGE, "2000"), ctx)).isEmpty();
        }

        @Test
        void noAgreementOnFileIsNeverReadAsNotAllowed() {
            assertThat(rule.evaluate(line(DeductionCategory.SOCIETY_CHARGE, "2000"), context(12, null, List.of()))).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = DeductionCategory.class, names = {"PAINTING", "CLEANING", "REPAIR", "UTILITY", "RENT"})
        void legalDuesDoNotNeedAClause(DeductionCategory category) {
            assertThat(rule.evaluate(line(category, "2000"), context(12, agreementCovering(), List.of()))).isEmpty();
        }
    }

    @Nested
    class R3WearAndTear {

        private final AuditRule rule = new WearAndTearRule(TestRules.r3());

        @ParameterizedTest
        @CsvSource({"10, false", "11, true", "24, true"}) // spec decision 24: 11 months or more
        void elevenMonthsIsTheThreshold(int months, boolean fires) {
            Optional<RuleResult> r = rule.evaluate(line(DeductionCategory.PAINTING, "20000"),
                    context(months, agreementCovering(), List.of()));

            assertThat(r.isPresent()).isEqualTo(fires);
            r.ifPresent(result -> assertThat(result.verdict()).isEqualTo(Verdict.QUESTIONABLE));
        }

        @Test
        void cleaningCountsToo() {
            assertThat(rule.evaluate(line(DeductionCategory.CLEANING, "5000"), context(12, agreementCovering(), List.of())))
                    .map(RuleResult::verdict).contains(Verdict.QUESTIONABLE);
        }

        @Test
        void aClauseMakingTheTenantPaySilencesTheRule() {
            AuditContext ctx = context(24, agreementCovering(DeductionCategory.PAINTING), List.of());

            assertThat(rule.evaluate(line(DeductionCategory.PAINTING, "20000"), ctx)).isEmpty();
        }

        @Test
        void withoutAnAgreementItStillFiresButAsksForTheAgreement() {
            Optional<RuleResult> r = rule.evaluate(line(DeductionCategory.PAINTING, "20000"), context(24, null, List.of()));

            assertThat(r).isPresent();
            assertThat(r.get().reason()).endsWith("Upload your agreement to confirm.");
        }

        @Test
        void repairsAreNotWearAndTear() {
            assertThat(rule.evaluate(line(DeductionCategory.REPAIR, "1500"), context(24, agreementCovering(), List.of())))
                    .isEmpty();
        }
    }

    private static final UUID IN_FINE = UUID.randomUUID();
    private static final UUID IN_DAMAGED = UUID.randomUUID();
    private static final UUID OUT_DAMAGED = UUID.randomUUID();

    private static LineInput.Evidence evidence(UUID id, DocumentPhase phase, EvidenceObservation seen) {
        return new LineInput.Evidence(id, phase, seen);
    }

    private static LineInput lineWith(DeductionCategory category, String amount, LineInput.Evidence... evidence) {
        return new LineInput(UUID.randomUUID(), "Item", category, new BigDecimal(amount), "Bathroom",
                List.of(evidence));
    }

    private static AuditContext withPrices(DeductionCategory category, String p90, int n) {
        return new AuditContext(IndianState.KA, com.sankalp.fairexit.model.FlatSize.TWO_BHK, "Bengaluru", RENT,
                new BigDecimal("100000"), 24, null, List.of(),
                Map.of(category, new PriceStat(new BigDecimal(p90).divide(BigDecimal.valueOf(2)), new BigDecimal(p90), n)));
    }

    @Nested
    class R3Version2 {

        private final AuditRule rule = new WearAndTearRule(TestRules.r3v2());

        @Test
        void stillFiresForRoutinePainting() {
            assertThat(rule.evaluate(lineWith(DeductionCategory.PAINTING, "20000"), context(24, null, List.of())))
                    .isPresent();
        }

        @Test
        void staysSilentWhenTheTenantsOwnMoveOutPhotosShowDamage() {
            LineInput damaged = lineWith(DeductionCategory.PAINTING, "20000",
                    evidence(OUT_DAMAGED, DocumentPhase.MOVE_OUT, EvidenceObservation.DAMAGE_PRESENT));

            assertThat(rule.evaluate(damaged, context(24, null, List.of()))).isEmpty();
            // version 1 did not look at photos, so old audits keep their meaning
            assertThat(new WearAndTearRule(TestRules.r3()).evaluate(damaged, context(24, null, List.of()))).isPresent();
        }
    }

    @Nested
    class R4PresentAtMoveIn {

        private final AuditRule rule = new PresentAtMoveInRule(TestRules.r4());

        @Test
        void aMoveInPhotoShowingTheDamageMakesItQuestionable() {
            Optional<RuleResult> r = rule.evaluate(lineWith(DeductionCategory.REPAIR, "1500",
                    evidence(IN_DAMAGED, DocumentPhase.MOVE_IN, EvidenceObservation.DAMAGE_PRESENT)),
                    context(12, null, List.of()));

            assertThat(r).map(RuleResult::verdict).contains(Verdict.QUESTIONABLE);
            assertThat(r.get().evidenceIds()).containsExactly(IN_DAMAGED);
            assertThat(r.get().reason()).contains("already there when you moved in");
        }

        @Test
        void otherPhotosSayNothing() {
            assertThat(rule.evaluate(lineWith(DeductionCategory.REPAIR, "1500",
                            evidence(IN_FINE, DocumentPhase.MOVE_IN, EvidenceObservation.NO_DAMAGE),
                            evidence(OUT_DAMAGED, DocumentPhase.MOVE_OUT, EvidenceObservation.DAMAGE_PRESENT)),
                    context(12, null, List.of()))).isEmpty();
            assertThat(rule.evaluate(lineWith(DeductionCategory.REPAIR, "1500"), context(12, null, List.of())))
                    .isEmpty();
        }
    }

    /** Module 7 "done when": above p90 with 10+ points is flagged; fewer points gives nothing to decide on. */
    @Nested
    class R5PriceCheck {

        private final AuditRule rule = new PriceCheckRule(TestRules.r5());

        @Test
        void aboveP90WithTenOrMorePricesIsQuestionable() {
            Optional<RuleResult> r = rule.evaluate(lineWith(DeductionCategory.REPAIR, "2001"),
                    withPrices(DeductionCategory.REPAIR, "2000", 10));

            assertThat(r).map(RuleResult::verdict).contains(Verdict.QUESTIONABLE);
            assertThat(r.get().reason()).contains("9 in 10 of 10 known prices").contains("₹2,000 or less");
        }

        @Test
        void atOrBelowP90IsNotFlagged() {
            assertThat(rule.evaluate(lineWith(DeductionCategory.REPAIR, "2000"),
                    withPrices(DeductionCategory.REPAIR, "2000", 50))).isEmpty();
        }

        @ParameterizedTest
        @CsvSource({"9", "1"})
        void withFewerThanTenPricesTheRangeIsNotTrusted(int n) {
            assertThat(rule.evaluate(lineWith(DeductionCategory.REPAIR, "99999"),
                    withPrices(DeductionCategory.REPAIR, "2000", n))).isEmpty();
        }

        @Test
        void noPriceDataAtAllSaysNothing() {
            assertThat(rule.evaluate(lineWith(DeductionCategory.REPAIR, "99999"), context(12, null, List.of())))
                    .isEmpty();
        }

        @Test
        void duesAreNeverPriceChecked() {
            assertThat(rule.evaluate(lineWith(DeductionCategory.UTILITY, "99999"),
                    withPrices(DeductionCategory.UTILITY, "2000", 50))).isEmpty();
        }

        @Test
        void throughTheEngineFewerPointsEndAsNeedsInfo() {
            LineInput tap = lineWith(DeductionCategory.REPAIR, "5000");

            assertThat(new AuditEngine().judge(tap, withPrices(DeductionCategory.REPAIR, "2000", 12),
                    TestRules.defaults()).verdict()).isEqualTo(Verdict.QUESTIONABLE);
            assertThat(new AuditEngine().judge(tap, withPrices(DeductionCategory.REPAIR, "2000", 9),
                    TestRules.defaults()).verdict()).isEqualTo(Verdict.NEEDS_INFO);
        }
    }

    @Nested
    class R6DamageWithEvidence {

        private final AuditRule rule = new DamageWithEvidenceRule(TestRules.r6());

        private LineInput tap(String amount, LineInput.Evidence... evidence) {
            return lineWith(DeductionCategory.REPAIR, amount, evidence);
        }

        private final LineInput.Evidence fineIn = evidence(IN_FINE, DocumentPhase.MOVE_IN, EvidenceObservation.NO_DAMAGE);
        private final LineInput.Evidence damagedOut =
                evidence(OUT_DAMAGED, DocumentPhase.MOVE_OUT, EvidenceObservation.DAMAGE_PRESENT);

        @Test
        void fineAtMoveInDamagedAtMoveOutAndPricedFairlyIsFair() {
            Optional<RuleResult> r = rule.evaluate(tap("1500", fineIn, damagedOut),
                    withPrices(DeductionCategory.REPAIR, "2000", 15));

            assertThat(r).map(RuleResult::verdict).contains(Verdict.FAIR);
            assertThat(r.get().evidenceIds()).containsExactly(IN_FINE, OUT_DAMAGED);
        }

        @Test
        void eachConditionIsNeeded() {
            AuditContext prices = withPrices(DeductionCategory.REPAIR, "2000", 15);

            assertThat(rule.evaluate(tap("1500", damagedOut), prices)).as("no move in photo").isEmpty();
            assertThat(rule.evaluate(tap("1500", fineIn), prices)).as("no move out damage").isEmpty();
            assertThat(rule.evaluate(tap("2500", fineIn, damagedOut), prices)).as("above p90").isEmpty();
            assertThat(rule.evaluate(tap("1500", fineIn, damagedOut), withPrices(DeductionCategory.REPAIR, "2000", 9)))
                    .as("too few prices").isEmpty();
            assertThat(rule.evaluate(tap("1500", fineIn, damagedOut,
                    evidence(IN_DAMAGED, DocumentPhase.MOVE_IN, EvidenceObservation.DAMAGE_PRESENT)), prices))
                    .as("another move in photo shows the damage").isEmpty();
        }
    }

    @Nested
    class R7NeedsInfo {

        private final AuditRule rule = new NeedsInfoRule(TestRules.r7());

        @ParameterizedTest
        @EnumSource(DeductionCategory.class)
        void alwaysAnswersWithAQuestionForEveryCategory(DeductionCategory category) {
            Optional<RuleResult> r = rule.evaluate(line(category, "100"), context(1, null, List.of()));

            assertThat(r).map(RuleResult::verdict).contains(Verdict.NEEDS_INFO);
            assertThat(r.get().reason()).startsWith("Not enough information to decide. Ask the landlord: Ask");
        }
    }
}
