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

/** One real price for one kind of work, in one city, for one flat size. */
@Entity
@Table(name = "price_points")
public class PricePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DeductionCategory category;

    @Column(nullable = false, updatable = false, length = 60)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "flat_size", nullable = false, updatable = false, length = 20)
    private FlatSize flatSize;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private PriceSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PricePoint() {
        // for JPA
    }

    public PricePoint(DeductionCategory category, String city, FlatSize flatSize, BigDecimal amount,
                      PriceSource source) {
        this.category = category;
        this.city = city;
        this.flatSize = flatSize;
        this.amount = amount;
        this.source = source;
        this.createdAt = Instant.now();
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

    public BigDecimal getAmount() {
        return amount;
    }
}
