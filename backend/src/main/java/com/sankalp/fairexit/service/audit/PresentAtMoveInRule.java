package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import com.sankalp.fairexit.model.Verdict;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rule 4. The tenant linked a move in photo that already shows the damage: the tenant
 * did not cause it. The photo's upload time is fixed by the vault's hash chain.
 */
public class PresentAtMoveInRule implements AuditRule {

    private final RuleSpec spec;

    public PresentAtMoveInRule(RuleSpec spec) {
        this.spec = spec;
    }

    @Override
    public String ref() {
        return spec.ref();
    }

    @Override
    public Optional<RuleResult> evaluate(LineInput line, AuditContext context) {
        List<UUID> photos = line.photos(DocumentPhase.MOVE_IN, EvidenceObservation.DAMAGE_PRESENT);
        if (photos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(Verdict.QUESTIONABLE, ref(),
                spec.reason(Map.of("item", line.item())), photos));
    }
}
