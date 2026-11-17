package com.sankalp.fairexit.service.reply;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.Verdict;
import com.sankalp.fairexit.service.audit.Rupees;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Builds two ready to send messages from an audit: a polite one to start with, and a
 * firm one if the landlord does not agree. Pure Java templates: no LLM, no database.
 *
 * Every amount is copied from the ReplyInput, which comes from the stored audit.
 * The finished text is then checked by ReplyAmountGuard before it is returned.
 */
@Component
public class ReplyGenerator {

    static final int REFUND_DAYS = 7;
    private static final String BULLET = "• ";

    /** What to ask for when a charge needs proof, in words for the landlord. */
    private static final Map<DeductionCategory, String> PROOF = new EnumMap<>(Map.of(
            DeductionCategory.PAINTING, "photos of the wall damage and the painter's invoice",
            DeductionCategory.CLEANING, "the cleaning invoice and what was cleaned beyond normal cleaning",
            DeductionCategory.REPAIR, "photos of the damage and the repair invoice",
            DeductionCategory.UTILITY, "a copy of the bill this amount comes from",
            DeductionCategory.RENT, "which months of rent this covers",
            DeductionCategory.SOCIETY_CHARGE, "the agreement clause or society rule that allows this charge",
            DeductionCategory.OTHER, "what this charge is for and the agreement clause that allows it"));

    public record Replies(String polite, String firm) {
    }

    public Replies generate(ReplyInput in) {
        Replies replies = new Replies(polite(in), firm(in));
        List<BigDecimal> allowed = allowedAmounts(in);
        ReplyAmountGuard.check(replies.polite(), allowed);
        ReplyAmountGuard.check(replies.firm(), allowed);
        return replies;
    }

    /** The only amounts a draft may contain: each line, the audit totals and the deposit. */
    static List<BigDecimal> allowedAmounts(ReplyInput in) {
        List<BigDecimal> allowed = new ArrayList<>();
        in.lines().forEach(l -> {
            allowed.add(l.amount());
            // An item name copied from the bill may mention money ("Painting Rs 500 extra").
            // It is the bill's own text, so it is allowed; nothing new is invented.
            allowed.addAll(ReplyAmountGuard.amountsIn(l.item()));
        });
        allowed.add(in.deposit());
        allowed.add(in.totalFair());
        allowed.add(in.totalDisputed());
        allowed.add(in.totalNeedsInfo());
        allowed.add(in.suggestedRefund());
        return allowed;
    }

    private String polite(ReplyInput in) {
        StringBuilder text = new StringBuilder("Hello,\n\n")
                .append("Thank you for sending the deductions from my security deposit of ")
                .append(Rupees.format(in.deposit()))
                .append(". I have gone through each charge.\n");

        section(text, in, Verdict.FAIR, in.totalFair(),
                "I agree to %s, %s in total:", line -> "");
        section(text, in, null, in.totalDisputed(),
                "I would like to talk about %s, %s in total:", line -> " " + politeReason(line, in));
        section(text, in, Verdict.NEEDS_INFO, in.totalNeedsInfo(),
                "Before I can agree to %s, %s in total, could you please share some details?",
                line -> " Could you please share " + PROOF.get(line.category()) + "?");

        text.append('\n');
        if (count(in, Verdict.NEEDS_INFO) > 0) {
            text.append("Based on the charges I agree to, the refund comes to ")
                    .append(Rupees.format(in.suggestedRefund()))
                    .append(". Once you share the details above, we can settle the remaining charges.\n");
        } else {
            text.append("Based on this, I request a refund of ")
                    .append(Rupees.format(in.suggestedRefund())).append(".\n");
        }
        text.append("\nThank you for your understanding. I hope we can settle this smoothly.\n");
        return signOff(text, in);
    }

    private String firm(ReplyInput in) {
        StringBuilder text = new StringBuilder("Hello,\n\n")
                .append("I am writing about the refund of my security deposit of ")
                .append(Rupees.format(in.deposit()))
                .append(" for the flat in ").append(in.city()).append(".\n");

        section(text, in, Verdict.FAIR, in.totalFair(),
                "I accept %s, %s in total:", line -> "");
        section(text, in, null, in.totalDisputed(),
                "I do not accept %s, %s in total:", line -> " " + firmReason(line, in));
        section(text, in, Verdict.NEEDS_INFO, in.totalNeedsInfo(),
                "I will consider %s, %s in total, only after you share proof:",
                line -> " Please share " + PROOF.get(line.category()) + ".");

        text.append("\nPlease return ")
                .append(Rupees.format(in.suggestedRefund()))
                .append(" within ").append(REFUND_DAYS).append(" days of this message.")
                .append(" I have kept a copy of your deduction list and of this reply.\n");
        return signOff(text, in);
    }

    /**
     * One block of lines with its total. verdict null means "disputed"
     * (QUESTIONABLE or NOT_IN_AGREEMENT). Nothing is written for an empty group.
     */
    private static void section(StringBuilder text, ReplyInput in, Verdict verdict, BigDecimal total,
                                String heading, Function<ReplyInput.Line, String> reason) {
        List<ReplyInput.Line> group = in.lines().stream()
                .filter(l -> verdict == null ? l.verdict().isDisputed() : l.verdict() == verdict)
                .toList();
        if (group.isEmpty()) {
            return;
        }
        String what = group.size() == 1 ? "this charge" : "these charges";
        text.append('\n').append(String.format(heading, what, Rupees.format(total))).append('\n');
        for (ReplyInput.Line line : group) {
            text.append(BULLET).append(line.item()).append(": ").append(Rupees.format(line.amount()))
                    .append('.').append(reason.apply(line)).append('\n');
        }
    }

    private static String politeReason(ReplyInput.Line line, ReplyInput in) {
        if (line.verdict() == Verdict.NOT_IN_AGREEMENT) {
            return "I could not find this charge in our rent agreement.";
        }
        if (line.fired("R3")) {
            return "After " + in.tenancyMonths() + " months of stay, this looks like normal wear and tear.";
        }
        if (line.fired("R4")) {
            return "This was already there when I moved in, as my move in photos show.";
        }
        if (line.fired("R5")) {
            return "This looks higher than the usual price for this work.";
        }
        return "I am not sure this charge is justified.";
    }

    private static String firmReason(ReplyInput.Line line, ReplyInput in) {
        if (line.verdict() == Verdict.NOT_IN_AGREEMENT) {
            return "Our rent agreement has no clause that allows this charge.";
        }
        if (line.fired("R3")) {
            return "After " + in.tenancyMonths() + " months of stay, this is normal wear and tear,"
                    + " which a tenant is not charged for.";
        }
        if (line.fired("R4")) {
            return "This damage was already there at move in. My move in photos, with their upload time,"
                    + " are kept in a tamper proof record.";
        }
        if (line.fired("R5")) {
            return "This is above the normal price range for this work in " + in.city() + ".";
        }
        return "This charge is not justified.";
    }

    private static long count(ReplyInput in, Verdict verdict) {
        return in.lines().stream().filter(l -> l.verdict() == verdict).count();
    }

    private static String signOff(StringBuilder text, ReplyInput in) {
        text.append("\nRegards,\n");
        if (in.tenantName() != null && !in.tenantName().isBlank()) {
            text.append(in.tenantName().trim()).append('\n');
        }
        return text.toString();
    }
}
