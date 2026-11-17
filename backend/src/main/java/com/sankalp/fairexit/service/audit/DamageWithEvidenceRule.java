package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import com.sankalp.fairexit.model.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rule 6. A fair charge, said honestly: the tenant's move in photo shows the item was fine,
 * the move out photo shows it damaged, no move in photo shows the damage, and the price
 * is within the trusted usual range.
 */
public class DamageWithEvidenceRule implements AuditRule {

    private final RuleSpec spec;
    private final Set<DeductionCategory> categories;
    private final int minPoints;

    public DamageWithEvidenceRule(RuleSpec spec) {
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
        List<UUID> fineAtMoveIn = line.photos(DocumentPhase.MOVE_IN, EvidenceObservation.NO_DAMAGE);
        List<UUID> damagedAtMoveOut = line.photos(DocumentPhase.MOVE_OUT, EvidenceObservation.DAMAGE_PRESENT);
        boolean damagedAtMoveIn = !line.photos(DocumentPhase.MOVE_IN, EvidenceObservation.DAMAGE_PRESENT).isEmpty();

        if (!categories.contains(line.category())
                || fineAtMoveIn.isEmpty()
                || damagedAtMoveOut.isEmpty()
                || damagedAtMoveIn
                || !PriceCheckRule.withinTrustedRange(line, context, minPoints)) {
            return Optional.empty();
        }
        List<UUID> evidence = new ArrayList<>(fineAtMoveIn);
        evidence.addAll(damagedAtMoveOut);
        return Optional.of(new RuleResult(Verdict.FAIR, ref(), spec.reason(Map.of("item", line.item())), evidence));
    }
}
