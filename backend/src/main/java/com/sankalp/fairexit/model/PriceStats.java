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
import java.util.UUID;

/** The usual price range for one (category, city, flat size), rebuilt each night. */
@Entity
@Table(name = "price_stats")
public class PriceStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeductionCategory category;

    @Column(nullable = false, length = 60)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "flat_size", nullable = false, length = 20)
    private FlatSize flatSize;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal p50;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal p90;

    @Column(nullable = false)
    private int n;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    protected PriceStats() {
        // for JPA
    }

    public PriceStats(DeductionCategory category, String city, FlatSize flatSize, BigDecimal p50, BigDecimal p90,
                      int n, Instant refreshedAt) {
        this.category = category;
        this.city = city;
        this.flatSize = flatSize;
        this.p50 = p50;
        this.p90 = p90;
        this.n = n;
        this.refreshedAt = refreshedAt;
    }

    public DeductionCategory getCategory() {
        return category;
    }

    public String getCity() {
        return city;
    }

    public FlatSize getFlatSize() {
        return flatSize;
    }

    public BigDecimal getP50() {
        return p50;
    }

    public BigDecimal getP90() {
        return p90;
    }

    public int getN() {
        return n;
    }
}
