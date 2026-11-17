package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One rented home. The owner is kept as a plain user_id column rather than a
 * @ManyToOne User: we only ever need the id for scoping, and this avoids lazy loading.
 */
@Entity
@Table(name = "tenancies")
public class Tenancy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private IndianState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "flat_size", nullable = false, length = 20)
    private FlatSize flatSize;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rent;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deposit;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "notice_days")
    private Integer noticeDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** How many documents are in this tenancy's hash chain. */
    @Column(name = "chain_length", nullable = false)
    private int chainLength;

    /** record_hash of the last document in the chain, null when the chain is empty. */
    @Column(name = "chain_head", length = 64)
    private String chainHead;

    protected Tenancy() {
        // for JPA
    }

    public Tenancy(UUID userId, String city, IndianState state, FlatSize flatSize,
                   BigDecimal rent, BigDecimal deposit,
                   LocalDate startDate, LocalDate endDate, Integer noticeDays) {
        this.userId = userId;
        this.city = city;
        this.state = state;
        this.flatSize = flatSize;
        this.rent = rent;
        this.deposit = deposit;
        this.startDate = startDate;
        this.endDate = endDate;
        this.noticeDays = noticeDays;
        this.createdAt = Instant.now();
    }

    /** Moves the chain head on by one document. Returns the new document's sequence number. */
    public int appendToChain(String recordHash) {
        chainLength++;
        chainHead = recordHash;
        return chainLength;
    }

    public int getChainLength() {
        return chainLength;
    }

    public String getChainHead() {
        return chainHead;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCity() {
        return city;
    }

    public IndianState getState() {
        return state;
    }

    public FlatSize getFlatSize() {
        return flatSize;
    }

    public BigDecimal getRent() {
        return rent;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Integer getNoticeDays() {
        return noticeDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
