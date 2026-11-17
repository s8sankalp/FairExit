package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.Verdict;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AuditOutcome(List<LineVerdict> verdicts, Totals totals) {

    /** evidenceIds: the vault documents behind the winning verdict. */
    public record LineVerdict(LineInput line, Verdict verdict, List<String> ruleRefs, String reason,
                              List<UUID> evidenceIds) {
    }

    /**
     * questionable = QUESTIONABLE + NOT_IN_AGREEMENT (everything the tenant disputes).
     * suggestedRefund = deposit minus only the charges found fair; lines that still
     * need information are not conceded.
     */
    public record Totals(BigDecimal claimed, BigDecimal fair, BigDecimal questionable,
                         BigDecimal needsInfo, BigDecimal suggestedRefund) {
    }
}
