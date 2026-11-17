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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * An immutable audit result. There are no setters: running the audit again
 * creates a new row, so an old result can always be explained exactly as it was.
 */
@Entity
@Table(name = "audits")
public class Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bill_id", nullable = false, updatable = false)
    private UUID billId;

    @Column(name = "rules_version", nullable = false, updatable = false)
    private String rulesVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rules_coverage", nullable = false, updatable = false, length = 20)
    private RulesCoverage rulesCoverage;

    @Column(name = "tenancy_months", nullable = false, updatable = false)
    private int tenancyMonths;

    @Column(name = "total_claimed", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalClaimed;

    @Column(name = "total_fair", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalFair;

    @Column(name = "total_questionable", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalQuestionable;

    @Column(name = "total_needs_info", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalNeedsInfo;

    @Column(name = "suggested_refund", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal suggestedRefund;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "audit", cascade = CascadeType.PERSIST)
    private List<AuditVerdict> verdicts = new ArrayList<>();

    protected Audit() {
        // for JPA
    }

    public Audit(UUID billId, String rulesVersion, RulesCoverage rulesCoverage, int tenancyMonths,
                 BigDecimal totalClaimed, BigDecimal totalFair, BigDecimal totalQuestionable,
                 BigDecimal totalNeedsInfo, BigDecimal suggestedRefund) {
        this.billId = billId;
        this.rulesVersion = rulesVersion;
        this.rulesCoverage = rulesCoverage;
        this.tenancyMonths = tenancyMonths;
        this.totalClaimed = totalClaimed;
        this.totalFair = totalFair;
        this.totalQuestionable = totalQuestionable;
        this.totalNeedsInfo = totalNeedsInfo;
        this.suggestedRefund = suggestedRefund;
        this.createdAt = Instant.now();
    }

    public void addVerdict(UUID lineId, Verdict verdict, List<String> ruleIds, String reason,
                           List<UUID> evidenceIds) {
        verdicts.add(new AuditVerdict(this, lineId, verdict, ruleIds, reason, evidenceIds));
    }

    public UUID getId() {
        return id;
    }

    public UUID getBillId() {
        return billId;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public RulesCoverage getRulesCoverage() {
        return rulesCoverage;
    }

    public int getTenancyMonths() {
        return tenancyMonths;
    }

    public BigDecimal getTotalClaimed() {
        return totalClaimed;
    }

    public BigDecimal getTotalFair() {
        return totalFair;
    }

    public BigDecimal getTotalQuestionable() {
        return totalQuestionable;
    }

    public BigDecimal getTotalNeedsInfo() {
        return totalNeedsInfo;
    }

    public BigDecimal getSuggestedRefund() {
        return suggestedRefund;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<AuditVerdict> getVerdicts() {
        return Collections.unmodifiableList(verdicts);
    }
}
