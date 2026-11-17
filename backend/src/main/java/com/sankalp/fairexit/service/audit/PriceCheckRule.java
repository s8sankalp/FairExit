package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.Verdict;
import com.sankalp.fairexit.service.audit.AuditContext.PriceStat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule 5. An amount above the 90th percentile of real prices for the same work, city and
 * flat size is QUESTIONABLE. With fewer than 10 prices the range is not trusted and the
 * rule says nothing (spec decision 25: never guess a price).
 */
public class PriceCheckRule implements AuditRule {

    private final RuleSpec spec;
    private final Set<DeductionCategory> categories;
    private final int minPoints;

    public PriceCheckRule(RuleSpec spec) {
        this.spec = spec;
        this.categories = spec.listParam("categories").stream()
                .map(DeductionCategory::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        this.minPoints = spec.intParam("minPoints", 10);
    }

    @Override
    public String ref() {
        return spec.ref();
    }

    @Override
    public Optional<RuleResult> evaluate(LineInput line, AuditContext context) {
        if (!categories.contains(line.category())) {
            return Optional.empty();
        }
        Optional<PriceStat> stat = context.priceFor(line.category()).filter(s -> s.n() >= minPoints);
        if (stat.isEmpty() || line.amount().compareTo(stat.get().p90()) <= 0) {
            return Optional.empty();
        }
        String reason = spec.reason(Map.of(
                "item", line.item(),
                "amount", Rupees.format(line.amount()),
                "n", String.valueOf(stat.get().n()),
                "category", line.category().label(),
                "flatSize", context.flatSize().label(),
                "city", context.city(),
                "p90", Rupees.format(stat.get().p90())));
        return Optional.of(new RuleResult(Verdict.QUESTIONABLE, ref(), reason));
    }

    /** Shared with rule 6: a trusted range exists and the amount is inside it. */
    static boolean withinTrustedRange(LineInput line, AuditContext context, int minPoints) {
        return context.priceFor(line.category())
                .filter(s -> s.n() >= minPoints)
                .map(s -> line.amount().compareTo(s.p90()) <= 0)
                .orElse(false);
    }
}
