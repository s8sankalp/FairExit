package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.BillSource;
import com.sankalp.fairexit.model.BillStatus;
import com.sankalp.fairexit.model.DeductionBill;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DeductionLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param statedTotal     the total written in the pasted message, if any
 * @param totalMismatch   true when statedTotal differs from the sum of the lines: warn the tenant
 * @param extractionError why the AI could not read the bill, when status is EXTRACTION_FAILED
 */
public record BillResponse(UUID id, UUID tenancyId, BillStatus status, BillSource source,
                           BigDecimal total, BigDecimal statedTotal, boolean totalMismatch,
                           String extractionError, List<Line> lines, Instant createdAt) {

    public record Line(UUID id, int position, String item, DeductionCategory category,
                       BigDecimal amount, String room, boolean editedByUser) {

        static Line from(DeductionLine l) {
            return new Line(l.getId(), l.getLineNo(), l.getItem(), l.getCategory(), l.getAmount(), l.getRoom(),
                    l.isEditedByUser());
        }
    }

    public static BillResponse from(DeductionBill bill) {
        return new BillResponse(bill.getId(), bill.getTenancyId(), bill.getStatus(), bill.getSource(),
                bill.total(), bill.getStatedTotal(), bill.totalMismatch(), bill.getExtractionError(),
                bill.getLines().stream().map(Line::from).toList(), bill.getCreatedAt());
    }
}
