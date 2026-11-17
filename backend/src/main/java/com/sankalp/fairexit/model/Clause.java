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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One clause, with the exact words it was read from, so the tenant can check it.
 * value holds what the clause says: {"tenantPays": true} for charge clauses,
 * {"amount": 150000} for deposit or rent, {"days": 30} for periods, {"months": 11} for lock in.
 */
@Entity
@Table(name = "clauses")
public class Clause {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false, updatable = false)
    private Agreement agreement;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    private UUID tenancyId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ClauseType type;

    @Column(updatable = false)
    private Integer page;

    @Column(nullable = false, updatable = false)
    private String quote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> value;

    @Enumerated(EnumType.STRING)
    @Column(name = "checked_against", nullable = false, updatable = false, length = 20)
    private QuoteSource checkedAgainst;

    protected Clause() {
        // for JPA
    }

    Clause(Agreement agreement, ClauseType type, Integer page, String quote, Map<String, Object> value,
           QuoteSource checkedAgainst) {
        this.agreement = agreement;
        this.tenancyId = agreement.getTenancyId();
        this.documentId = agreement.getDocumentId();
        this.type = type;
        this.page = page;
        this.quote = quote;
        this.value = Map.copyOf(value);
        this.checkedAgainst = checkedAgainst;
    }

    /** True when this is a charge clause that says the tenant pays. */
    public boolean tenantPays() {
        return type.chargeCategory() != null && Boolean.TRUE.equals(value.get("tenantPays"));
    }

    public ClauseType getType() {
        return type;
    }

    public Integer getPage() {
        return page;
    }

    public String getQuote() {
        return quote;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    public QuoteSource getCheckedAgainst() {
        return checkedAgainst;
    }
}
