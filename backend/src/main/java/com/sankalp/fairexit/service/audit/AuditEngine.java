package com.sankalp.fairexit.service.audit;

import com.sankalp.fairexit.model.Verdict;
import com.sankalp.fairexit.service.audit.AuditOutcome.LineVerdict;
import com.sankalp.fairexit.service.audit.AuditOutcome.Totals;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The Deduction Audit. Pure Java: no database, no clock, no LLM.
 * Same lines + same context + same rule set always give the same outcome.
 *
 * For each line every check runs. The strongest verdict wins
 * (NOT_IN_AGREEMENT > QUESTIONABLE > FAIR), and every rule that fired is listed.
 * If no check fires, the fallback rule gives NEEDS_INFO with a question to ask.
 */
@Component
public class AuditEngine {

    public AuditOutcome run(List<LineInput> lines, AuditContext context, RuleSet rules) {
        List<LineVerdict> verdicts = lines.stream()
                .map(line -> judge(line, context, rules))
                .toList();
        return new AuditOutcome(verdicts, totals(verdicts, context.deposit()));
    }

    LineVerdict judge(LineInput line, AuditContext context, RuleSet rules) {
        List<RuleResult> fired = rules.checks().stream()
                .map(rule -> rule.evaluate(line, context))
                .flatMap(Optional::stream)
                .toList();

        if (fired.isEmpty()) {
            RuleResult fallback = rules.fallback().evaluate(line, context)
                    .orElseThrow(() -> new IllegalStateException("The fallback rule must always answer"));
            return new LineVerdict(line, fallback.verdict(), List.of(fallback.ruleRef()), fallback.reason(),
                    fallback.evidenceIds());
        }

        Verdict strongest = fired.stream()
                .map(RuleResult::verdict)
                .max(Comparator.comparingInt(Verdict::strength))
                .orElseThrow();
        List<RuleResult> winners = fired.stream().filter(r -> r.verdict() == strongest).toList();
        String reason = winners.stream().map(RuleResult::reason).collect(Collectors.joining(" "));
        List<UUID> evidence = winners.stream().flatMap(r -> r.evidenceIds().stream()).distinct().toList();
        List<String> refs = fired.stream().map(RuleResult::ruleRef).toList();
        return new LineVerdict(line, strongest, refs, reason, evidence);
    }

    private static Totals totals(List<LineVerdict> verdicts, BigDecimal deposit) {
        BigDecimal claimed = sum(verdicts, v -> true);
        BigDecimal fair = sum(verdicts, v -> v.verdict() == Verdict.FAIR);
        BigDecimal questionable = sum(verdicts, v -> v.verdict().isDisputed());
        BigDecimal needsInfo = sum(verdicts, v -> v.verdict() == Verdict.NEEDS_INFO);
        BigDecimal refund = deposit.subtract(fair).max(BigDecimal.ZERO);
        return new Totals(claimed, fair, questionable, needsInfo, refund);
    }

    private static BigDecimal sum(List<LineVerdict> verdicts, Predicate<LineVerdict> filter) {
        return verdicts.stream()
                .filter(filter)
                .map(v -> v.line().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
