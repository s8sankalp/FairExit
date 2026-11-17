package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.EvidenceObservation;
import java.util.List;
import java.util.UUID;

public record EvidenceLinkResponse(UUID lineId, List<Link> links) {

    public record Link(UUID documentId, DocumentPhase phase, String room, String item,
                       EvidenceObservation observation) {
    }
}
