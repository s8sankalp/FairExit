package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.model.Verdict;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditResponse(
        UUID id,
        UUID billId,
        String rulesVersion,
        RulesCoverage rulesCoverage,
        int tenancyMonths,
        Totals totals,
        List<LineVerdict> verdicts,
        String disclaimer,
        Instant createdAt) {

    public record Totals(BigDecimal claimed, BigDecimal fair, BigDecimal questionable,
                         BigDecimal needsInfo, BigDecimal suggestedRefund) {
    }

    public record LineVerdict(UUID lineId, int position, String item, DeductionCategory category,
                              BigDecimal amount, Verdict verdict, List<String> ruleIds, String reason,
                              List<String> evidenceIds) {
    }
}
