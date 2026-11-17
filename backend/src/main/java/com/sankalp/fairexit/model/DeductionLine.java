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
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "deduction_lines")
public class DeductionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false, updatable = false)
    private DeductionBill bill;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(nullable = false, length = 200)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeductionCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 60)
    private String room;

    @Column(name = "edited_by_user", nullable = false)
    private boolean editedByUser;

    protected DeductionLine() {
        // for JPA
    }

    DeductionLine(DeductionBill bill, int lineNo, String item, DeductionCategory category,
                  BigDecimal amount, String room, boolean editedByUser) {
        this.bill = bill;
        this.lineNo = lineNo;
        this.item = item;
        this.category = category;
        this.amount = amount;
        this.room = room;
        this.editedByUser = editedByUser;
    }

    public UUID getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getItem() {
        return item;
    }

    public DeductionCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getRoom() {
        return room;
    }

    /** True when the item, category, amount and room are all the same. */
    public boolean sameContent(String otherItem, DeductionCategory otherCategory,
                               BigDecimal otherAmount, String otherRoom) {
        return item.equals(otherItem)
                && category == otherCategory
                && amount.compareTo(otherAmount) == 0
                && java.util.Objects.equals(room, otherRoom);
    }

    public boolean isEditedByUser() {
        return editedByUser;
    }
}
