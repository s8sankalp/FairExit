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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** One landlord deduction bill. Lines are owned by the bill and saved with it. */
@Entity
@Table(name = "deduction_bills")
public class DeductionBill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    private UUID tenancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private BillSource source;

    @Column(name = "raw_text_hash", length = 64)
    private String rawTextHash;

    /** The vault document the bill was read from, when it came from a photo or PDF. */
    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    /** The pasted message after masking. The unmasked text is never stored. */
    @Column(name = "masked_text")
    private String maskedText;

    @Column(name = "stated_total", precision = 12, scale = 2)
    private BigDecimal statedTotal;

    @Column(name = "extraction_error", length = 300)
    private String extractionError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<DeductionLine> lines = new ArrayList<>();

    protected DeductionBill() {
        // for JPA
    }

    private DeductionBill(UUID tenancyId, BillSource source, BillStatus status) {
        this.tenancyId = tenancyId;
        this.source = source;
        this.status = status;
        this.createdAt = Instant.now();
    }

    /** A bill typed in by the tenant is confirmed straight away. */
    public static DeductionBill manual(UUID tenancyId) {
        return new DeductionBill(tenancyId, BillSource.MANUAL, BillStatus.CONFIRMED);
    }

    /** A pasted message waiting for the AI to read it. */
    public static DeductionBill fromText(UUID tenancyId, String maskedText, String maskedTextHash) {
        DeductionBill bill = new DeductionBill(tenancyId, BillSource.TEXT, BillStatus.EXTRACTING);
        bill.maskedText = maskedText;
        bill.rawTextHash = maskedTextHash;
        return bill;
    }

    /** A photo or PDF of the bill, waiting for the AI to read it. */
    public static DeductionBill fromDocument(UUID tenancyId, UUID documentId, String fileSha256) {
        DeductionBill bill = new DeductionBill(tenancyId, BillSource.FILE, BillStatus.EXTRACTING);
        bill.documentId = documentId;
        bill.rawTextHash = fileSha256;
        return bill;
    }

    public void addLine(String item, DeductionCategory category, BigDecimal amount, String room) {
        addLine(item, category, amount, room, false);
    }

    public void addLine(String item, DeductionCategory category, BigDecimal amount, String room,
                        boolean editedByUser) {
        lines.add(new DeductionLine(this, lines.size() + 1, item, category, amount, room, editedByUser));
    }

    public void clearLines() {
        lines.clear();
    }

    /** The AI's lines are in; the tenant still has to confirm them. */
    public void markExtracted(BigDecimal statedTotal) {
        this.statedTotal = statedTotal;
        this.extractionError = null;
        this.status = BillStatus.EXTRACTED;
    }

    public void markExtractionFailed(String message) {
        this.extractionError = message;
        this.status = BillStatus.EXTRACTION_FAILED;
    }

    public void markConfirmed() {
        this.extractionError = null;
        this.status = BillStatus.CONFIRMED;
    }

    public boolean isExtracting() {
        return status == BillStatus.EXTRACTING;
    }

    /** Lines can change until the first audit. After that a verdict points at each line. */
    public boolean linesCanChange() {
        return status != BillStatus.AUDITED;
    }

    /** True when the message states a total that differs from the sum of the lines. */
    public boolean totalMismatch() {
        return statedTotal != null && !lines.isEmpty() && statedTotal.compareTo(total()) != 0;
    }

    public void markAudited() {
        this.status = BillStatus.AUDITED;
    }

    public boolean canBeAudited() {
        return status == BillStatus.CONFIRMED || status == BillStatus.AUDITED;
    }

    public BigDecimal total() {
        return lines.stream().map(DeductionLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public BillStatus getStatus() {
        return status;
    }

    public BillSource getSource() {
        return source;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getMaskedText() {
        return maskedText;
    }

    public BigDecimal getStatedTotal() {
        return statedTotal;
    }

    public String getExtractionError() {
        return extractionError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<DeductionLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
