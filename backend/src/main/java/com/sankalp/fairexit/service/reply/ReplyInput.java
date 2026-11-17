package com.sankalp.fairexit.service.reply;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.Verdict;
import java.math.BigDecimal;
import java.util.List;

/**
 * Everything a reply may say. Every amount here comes from a stored audit
 * (or, for the deposit, from the tenancy). The generator adds no numbers of its own.
 */
public record ReplyInput(
        String tenantName,
        String city,
        BigDecimal deposit,
        int tenancyMonths,
        List<Line> lines,
        BigDecimal totalFair,
        BigDecimal totalDisputed,
        BigDecimal totalNeedsInfo,
        BigDecimal suggestedRefund) {

    public ReplyInput {
        lines = List.copyOf(lines);
    }

    public record Line(String item, DeductionCategory category, BigDecimal amount,
                       Verdict verdict, List<String> ruleIds) {

        public Line {
            ruleIds = List.copyOf(ruleIds);
        }

        /** True when a rule with this id (any version) fired, for example "R3". */
        public boolean fired(String ruleId) {
            return ruleIds.stream().anyMatch(ref -> ref.equals(ruleId) || ref.startsWith(ruleId + "@"));
        }
    }
}
