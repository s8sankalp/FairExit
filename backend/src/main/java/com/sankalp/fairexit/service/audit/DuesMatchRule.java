package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.Verdict;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Rule 1. A utility charge that matches a bill on file, or a rent charge that is a whole
 * number of months of the agreed rent, is a fair due.
 */
public class DuesMatchRule implements AuditRule {

    private final RuleSpec spec;
    private final BigDecimal tolerance;
    private final int maxRentMonths;

    public DuesMatchRule(RuleSpec spec) {
        this.spec = spec;
        this.tolerance = BigDecimal.valueOf(spec.intParam("tolerance", 1));
        this.maxRentMonths = spec.intParam("maxRentMonths", 3);
    }

    @Override
    public String ref() {
        return spec.ref();
    }

    @Override
    public Optional<RuleResult> evaluate(LineInput line, AuditContext context) {
        if (line.category() == DeductionCategory.UTILITY) {
            return context.knownBills().stream()
                    .filter(bill -> bill.category() == DeductionCategory.UTILITY)
                    .filter(bill -> close(line.amount(), bill.amount()))
                    .findFirst()
                    .map(bill -> fair("the utility bill of " + Rupees.format(bill.amount()) + " on file"));
        }
        if (line.category() == DeductionCategory.RENT && context.rent() != null) {
            for (int months = 1; months <= maxRentMonths; months++) {
                if (close(line.amount(), context.rent().multiply(BigDecimal.valueOf(months)))) {
                    return Optional.of(fair(months + (months == 1 ? " month" : " months")
                            + " of the agreed rent (" + Rupees.format(context.rent()) + " a month)"));
                }
            }
        }
        return Optional.empty();
    }

    private boolean close(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    private RuleResult fair(String match) {
        return new RuleResult(Verdict.FAIR, ref(), spec.reason(Map.of("match", match)));
    }
}
