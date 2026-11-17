package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_verdicts")
public class AuditVerdict {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false, updatable = false)
    private Audit audit;

    @Column(name = "line_id", nullable = false, updatable = false)
    private UUID lineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Verdict verdict;

    /** Every rule that fired, for example ["R3@1"], so the reason is traceable. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_ids", nullable = false, updatable = false)
    private List<String> ruleIds;

    @Column(nullable = false, updatable = false)
    private String reason;

    /** The vault documents the verdict relied on (rules 4 and 6). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_ids", nullable = false, updatable = false)
    private List<String> evidenceIds = List.of();

    protected AuditVerdict() {
        // for JPA
    }

    AuditVerdict(Audit audit, UUID lineId, Verdict verdict, List<String> ruleIds, String reason,
                 List<UUID> evidenceIds) {
        this.audit = audit;
        this.lineId = lineId;
        this.verdict = verdict;
        this.ruleIds = List.copyOf(ruleIds);
        this.reason = reason;
        this.evidenceIds = evidenceIds.stream().map(UUID::toString).toList();
    }

    public UUID getLineId() {
        return lineId;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public List<String> getRuleIds() {
        return ruleIds;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }
}
