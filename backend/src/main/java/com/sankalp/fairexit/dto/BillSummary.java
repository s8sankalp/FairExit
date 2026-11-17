package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.BillSource;
import com.sankalp.fairexit.model.BillStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row in a tenancy's list of bills. latestAuditId is null until the bill is audited. */
public record BillSummary(UUID id, BillStatus status, BillSource source, BigDecimal total, int lineCount,
                          UUID latestAuditId, Instant createdAt) {
}
