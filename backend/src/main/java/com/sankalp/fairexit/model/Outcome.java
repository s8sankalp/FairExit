package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What the tenant finally got back. One per tenancy; recording it again updates it. */
@Entity
@Table(name = "outcomes")
public class Outcome {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenancy_id", nullable = false, updatable = false, unique = true)
    private UUID tenancyId;

    @Column(name = "building_id")
    private UUID buildingId;

    @Column(name = "amount_returned", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountReturned;

    @Column(name = "days_taken", nullable = false)
    private int daysTaken;

    @Column(name = "receipt_document_id")
    private UUID receiptDocumentId;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Outcome() {
        // for JPA
    }

    public Outcome(UUID tenancyId, Instant now) {
        this.tenancyId = tenancyId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void record(BigDecimal amountReturned, int daysTaken, UUID receiptDocumentId, Instant now) {
        this.amountReturned = amountReturned;
        this.daysTaken = daysTaken;
        this.receiptDocumentId = receiptDocumentId;
        // "Verified" means a stored, hashed receipt backs the numbers, not just the tenant's word
        this.verified = receiptDocumentId != null;
        this.updatedAt = now;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public BigDecimal getAmountReturned() {
        return amountReturned;
    }

    public int getDaysTaken() {
        return daysTaken;
    }

    public UUID getReceiptDocumentId() {
        return receiptDocumentId;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
