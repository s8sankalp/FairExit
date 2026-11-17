package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One bill line as the engine sees it, with the vault photos the tenant linked to it.
 */
public record LineInput(UUID lineId, String item, DeductionCategory category, BigDecimal amount, String room,
                        List<Evidence> evidence) {

    public LineInput {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    /** A line with no photos linked. */
    public LineInput(UUID lineId, String item, DeductionCategory category, BigDecimal amount, String room) {
        this(lineId, item, category, amount, room, List.of());
    }

    /** A vault photo linked to the line, with when it was taken and what the tenant says it shows. */
    public record Evidence(UUID documentId, DocumentPhase phase, EvidenceObservation observation) {
    }

    /** Photos from this phase showing this observation. */
    public List<UUID> photos(DocumentPhase phase, EvidenceObservation observation) {
        return evidence.stream()
                .filter(e -> e.phase() == phase && e.observation() == observation)
                .map(Evidence::documentId)
                .toList();
    }
}
