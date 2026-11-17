package com.sankalp.fairexit.service.reply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import com.sankalp.fairexit.service.audit.AuditContext;
import com.sankalp.fairexit.service.audit.AuditContext.AgreementTerms;
import com.sankalp.fairexit.service.audit.AuditContext.KnownBill;
import com.sankalp.fairexit.service.audit.AuditEngine;
import com.sankalp.fairexit.service.audit.AuditOutcome;
import com.sankalp.fairexit.service.audit.LineInput;
import com.sankalp.fairexit.service.audit.Rupees;
import com.sankalp.fairexit.service.audit.TestRules;
import com.sankalp.fairexit.service.audit.WorkedExample;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplyGeneratorTest {

    private final ReplyGenerator generator = new ReplyGenerator();

    /** Turns an engine result into reply input, the same way ReplyService does from a stored audit. */
    static ReplyInput input(AuditOutcome outcome, AuditContext ctx, String name) {
        return new ReplyInput(name, ctx.city(), ctx.deposit(), ctx.tenancyMonths(),
                outcome.verdicts().stream()
                        .map(v -> new ReplyInput.Line(v.line().item(), v.line().category(), v.line().amount(),
                                v.verdict(), v.ruleRefs()))
                        .toList(),
                outcome.totals().fair(), outcome.totals().questionable(), outcome.totals().needsInfo(),
                outcome.totals().suggestedRefund());
    }

    private static ReplyInput workedExample() {
        AuditOutcome outcome = new AuditEngine().run(WorkedExample.lines(), WorkedExample.context(), TestRules.defaults());
        return input(outcome, WorkedExample.context(), "Asha Rao");
    }

    // ------------------------------------------------------------------
    // Module 5 "done when": draft amounts always match the audit
    // ------------------------------------------------------------------

    @Test
    void workedExampleDraftsUseExactlyTheAuditsAmounts() {
        ReplyInput in = workedExample();
        ReplyGenerator.Replies replies = generator.generate(in);

        for (String draft : List.of(replies.polite(), replies.firm())) {
            // Every amount in the text is one the audit produced ...
            assertThat(ReplyAmountGuard.amountsIn(draft))
                    .isNotEmpty()
                    .allSatisfy(a -> assertThat(ReplyGenerator.allowedAmounts(in))
                            .anySatisfy(allowed -> assertThat(allowed).isEqualByComparingTo(a)));
            // ... and every line and the refund are actually in it: the report's numbers
            assertThat(draft).contains("₹55,000", "₹2,000", "₹8,000", "₹1,500", "₹3,200",
                    "₹1,50,000", "₹65,000", "₹4,700", "₹1,45,300");
        }
    }

    /**
     * The same promise over 500 random bills: random items, categories, amounts,
     * tenancy lengths, agreements and utility bills, run through the real engine.
     * Fixed seed, so a failure can always be reproduced.
     */
    @Test
    void draftAmountsMatchTheAuditForAnyBill() {
        Random random = new Random(42);
        AuditEngine engine = new AuditEngine();
        DeductionCategory[] categories = DeductionCategory.values();

        for (int run = 0; run < 500; run++) {
            int lineCount = 1 + random.nextInt(8);
            List<LineInput> lines = new ArrayList<>();
            for (int i = 0; i < lineCount; i++) {
                BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(200_000))
                        .add(random.nextBoolean() ? new BigDecimal("0.50") : BigDecimal.ZERO);
                lines.add(new LineInput(UUID.randomUUID(), "Item " + i,
                        categories[random.nextInt(categories.length)], amount, null));
            }
            Set<DeductionCategory> agreed = EnumSet.noneOf(DeductionCategory.class);
            for (DeductionCategory c : categories) {
                if (random.nextInt(4) == 0) {
                    agreed.add(c);
                }
            }
            BigDecimal rent = BigDecimal.valueOf(5_000 + random.nextInt(50_000));
            AuditContext ctx = new AuditContext(IndianState.KA, FlatSize.TWO_BHK, "Bengaluru", rent,
                    BigDecimal.valueOf(10_000 + random.nextInt(400_000)), random.nextInt(48),
                    random.nextBoolean() ? new AgreementTerms(agreed) : null,
                    List.of(new KnownBill(DeductionCategory.UTILITY, lines.get(0).amount())));

            AuditOutcome outcome = engine.run(lines, ctx, TestRules.defaults());
            ReplyInput in = input(outcome, ctx, "Tenant");
            ReplyGenerator.Replies replies = generator.generate(in);

            for (String draft : List.of(replies.polite(), replies.firm())) {
                List<BigDecimal> allowed = ReplyGenerator.allowedAmounts(in);
                assertThat(ReplyAmountGuard.amountsIn(draft))
                        .as("run %d", run)
                        .allSatisfy(a -> assertThat(allowed)
                                .anySatisfy(x -> assertThat(x).isEqualByComparingTo(a)));
                assertThat(draft).as("run %d refund", run).contains(Rupees.format(in.suggestedRefund()));
                for (LineInput l : lines) {
                    assertThat(draft).as("run %d line", run).contains(Rupees.format(l.amount()));
                }
            }
        }
    }

    @Test
    void theGuardRefusesADraftWithAnAmountTheAuditNeverProduced() {
        List<BigDecimal> allowed = List.of(new BigDecimal("55000"), new BigDecimal("150000"));

        ReplyAmountGuard.check("Painting ₹55,000. Deposit ₹1,50,000.", allowed);
        assertThatThrownBy(() -> ReplyAmountGuard.check("Please return ₹1,45,000.", allowed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("145000");
        assertThatThrownBy(() -> ReplyAmountGuard.check("Painting Rs. 60000", allowed))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theGuardReadsEveryWayOfWritingRupees() {
        assertThat(ReplyAmountGuard.amountsIn("₹1,50,000 and Rs.2000 and rs 300 and INR 45.50 and ₹ 10"))
                .extracting(BigDecimal::toPlainString)
                .containsExactly("150000", "2000", "300", "45.50", "10");
        // "24 months" and "7 days" are not money
        assertThat(ReplyAmountGuard.amountsIn("After 24 months, within 7 days")).isEmpty();
    }

    // ------------------------------------------------------------------
    // What the drafts say
    // ------------------------------------------------------------------

    @Test
    void politeDraftAgreesAsksAndProposesTheRefund() {
        String polite = generator.generate(workedExample()).polite();

        // The research report's draft: "I agree to the tap and electricity charges of ₹4,700 ...
        // Please return ₹1,45,300."
        assertThat(polite)
                .startsWith("Hello,")
                .contains("I agree to these charges, ₹4,700 in total:")
                .contains("• Broken bathroom tap: ₹1,500.")
                .contains("• Pending electricity bill: ₹3,200.")
                .contains("I would like to talk about these charges, ₹65,000 in total:")
                .contains("• Full flat repainting: ₹55,000. After 24 months of stay, this looks like normal wear and tear.")
                .contains("• Lift usage: ₹2,000. I could not find this charge in our rent agreement.")
                .contains("• Deep cleaning: ₹8,000. This looks higher than the usual price for this work.")
                .doesNotContain("share some details")
                .contains("Based on this, I request a refund of ₹1,45,300.")
                .endsWith("Regards,\nAsha Rao\n");
    }

    @Test
    void firmDraftRefusesAndSetsADeadline() {
        String firm = generator.generate(workedExample()).firm();

        assertThat(firm)
                .contains("for the flat in Bengaluru")
                .contains("I accept these charges, ₹4,700 in total:")
                .contains("I do not accept these charges, ₹65,000 in total:")
                .contains("Our rent agreement has no clause that allows this charge.")
                .contains("which a tenant is not charged for")
                .contains("This is above the normal price range for this work in Bengaluru.")
                .contains("Please return ₹1,45,300 within 7 days of this message.");
    }

    @Test
    void whenEverythingIsFairThereIsNothingToDisputeOrAsk() {
        LineInput rent = new LineInput(UUID.randomUUID(), "Last month rent", DeductionCategory.RENT,
                new BigDecimal("25000"), null);
        AuditContext ctx = new AuditContext(IndianState.KL, FlatSize.ONE_BHK, "Kochi", new BigDecimal("25000"),
                new BigDecimal("75000"), 12, null, List.of());
        ReplyInput in = input(new AuditEngine().run(List.of(rent), ctx, TestRules.defaults()), ctx, null);

        ReplyGenerator.Replies replies = generator.generate(in);

        assertThat(replies.polite())
                .contains("I agree to this charge, ₹25,000 in total:")
                .doesNotContain("talk about").doesNotContain("share some details")
                .contains("Based on this, I request a refund of ₹50,000.")
                .endsWith("Regards,\n"); // no name known
    }

    @Test
    void draftsUseNoDashesOrInternalWords() {
        ReplyGenerator.Replies replies = generator.generate(workedExample());

        for (String draft : List.of(replies.polite(), replies.firm())) {
            assertThat(draft)
                    .doesNotContain("—").doesNotContain("–").doesNotContain("\n- ")
                    // words meant for the tenant, not the landlord, never leak in
                    .doesNotContain("NEEDS_INFO").doesNotContain("R3@1").doesNotContain("Upload your agreement")
                    .doesNotContain("legal advice");
        }
    }

    @Test
    void anAmountInsideAnItemNameIsTheBillsOwnTextAndIsAllowed() {
        LineInput line = new LineInput(UUID.randomUUID(), "Painting Rs 500 extra per room", DeductionCategory.PAINTING,
                new BigDecimal("4000"), null);
        AuditContext ctx = new AuditContext(IndianState.KA, FlatSize.ONE_BHK, "Mysuru", new BigDecimal("12000"),
                new BigDecimal("40000"), 18, null, List.of());
        ReplyInput in = input(new AuditEngine().run(List.of(line), ctx, TestRules.defaults()), ctx, "A");

        assertThat(generator.generate(in).polite()).contains("Painting Rs 500 extra per room: ₹4,000.");
    }

    @Test
    void theSameAuditAlwaysGivesTheSameDrafts() {
        assertThat(generator.generate(workedExample())).isEqualTo(generator.generate(workedExample()));
    }
}
