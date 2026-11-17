package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evidence_packs")
public class EvidencePack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    private UUID tenancyId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 300)
    private String storageKey;

    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "chain_head", updatable = false, length = 64)
    private String chainHead;

    @Column(name = "chain_valid", nullable = false, updatable = false)
    private boolean chainValid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected EvidencePack() {
        // for JPA
    }

    public EvidencePack(UUID id, UUID auditId, UUID tenancyId, String storageKey, String sha256, String chainHead,
                        boolean chainValid, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.auditId = auditId;
        this.tenancyId = tenancyId;
        this.storageKey = storageKey;
        this.sha256 = sha256;
        this.chainHead = chainHead;
        this.chainValid = chainValid;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSha256() {
        return sha256;
    }

    public String getChainHead() {
        return chainHead;
    }

    public boolean isChainValid() {
        return chainValid;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
