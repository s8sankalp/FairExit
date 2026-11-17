package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** "This vault photo is about this bill line, and it shows ..." */
@Entity
@Table(name = "line_evidence")
public class LineEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "line_id", nullable = false, updatable = false)
    private UUID lineId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private EvidenceObservation observation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LineEvidence() {
        // for JPA
    }

    public LineEvidence(UUID lineId, UUID documentId, EvidenceObservation observation) {
        this.lineId = lineId;
        this.documentId = documentId;
        this.observation = observation;
        this.createdAt = Instant.now();
    }

    public UUID getLineId() {
        return lineId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public EvidenceObservation getObservation() {
        return observation;
    }
}
