package com.sankalp.fairexit.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One reading of a rent agreement document. Uploading a new agreement makes a new row. */
@Entity
@Table(name = "agreements")
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    private UUID tenancyId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgreementStatus status;

    @Column(length = 300)
    private String error;

    @Column(name = "dropped_clauses", nullable = false)
    private int droppedClauses;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Clause> clauses = new ArrayList<>();

    protected Agreement() {
        // for JPA
    }

    public Agreement(UUID tenancyId, UUID documentId) {
        this.tenancyId = tenancyId;
        this.documentId = documentId;
        this.status = AgreementStatus.EXTRACTING;
        this.createdAt = Instant.now();
    }

    public void addClause(ClauseType type, Integer page, String quote, Map<String, Object> value, QuoteSource source) {
        clauses.add(new Clause(this, type, page, quote, value, source));
    }

    public void markExtracted(int dropped) {
        this.droppedClauses = dropped;
        this.status = AgreementStatus.EXTRACTED;
    }

    public void markFailed(String message) {
        this.error = message;
        this.status = AgreementStatus.FAILED;
    }

    public boolean isExtracting() {
        return status == AgreementStatus.EXTRACTING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public AgreementStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public int getDroppedClauses() {
        return droppedClauses;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Clause> getClauses() {
        return Collections.unmodifiableList(clauses);
    }
}
