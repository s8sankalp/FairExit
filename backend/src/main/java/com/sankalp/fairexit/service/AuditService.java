package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.FairExitMetrics;
import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.Audit;
import com.sankalp.fairexit.model.AuditVerdict;
import com.sankalp.fairexit.model.DeductionBill;
import com.sankalp.fairexit.model.DeductionLine;
import com.sankalp.fairexit.model.RulesCoverage;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.AuditRepository;
import com.sankalp.fairexit.service.audit.AuditContext;
import com.sankalp.fairexit.service.audit.AuditEngine;
import com.sankalp.fairexit.service.audit.AuditOutcome;
import com.sankalp.fairexit.service.audit.LineInput;
import com.sankalp.fairexit.service.audit.RuleSet;
import com.sankalp.fairexit.service.vault.VaultService;
import com.sankalp.fairexit.model.DeductionCategory;
import java.util.Set;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Glue around the pure AuditEngine: loads the bill and tenancy, builds the context,
 * runs the engine and stores the result. All decisions are made in the engine.
 */
@Service
public class AuditService {

    static final String DISCLAIMER = "This is information, not legal advice.";
    static final String GENERAL_GUIDANCE_NOTE =
            " FairExit has no rules specific to your state yet, so this is general guidance.";

    private final BillService billService;
    private final TenancyService tenancyService;
    private final RuleCatalog ruleCatalog;
    private final AuditEngine engine;
    private final AuditRepository audits;
    private final AgreementService agreements;
    private final VaultService vault;
    private final EvidenceService evidence;
    private final PriceService prices;
    private final FairExitMetrics metrics;
    private final Clock clock;

    public AuditService(BillService billService, TenancyService tenancyService, RuleCatalog ruleCatalog,
                        AuditEngine engine, AuditRepository audits, AgreementService agreements,
                        VaultService vault, EvidenceService evidence, PriceService prices,
                        FairExitMetrics metrics, Clock clock) {
        this.billService = billService;
        this.tenancyService = tenancyService;
        this.ruleCatalog = ruleCatalog;
        this.engine = engine;
        this.audits = audits;
        this.agreements = agreements;
        this.vault = vault;
        this.evidence = evidence;
        this.prices = prices;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public AuditResponse run(UUID userId, UUID billId) {
        DeductionBill bill = billService.findOwned(userId, billId);
        if (!bill.canBeAudited()) {
            throw new ApiException(HttpStatus.CONFLICT, "Confirm the bill lines before running the audit (PUT /api/bills/{id}/lines)");
        }
        Tenancy tenancy = tenancyService.findOwned(userId, bill.getTenancyId());

        // Everything the rules need is loaded here, up front; the engine itself does no I/O
        Set<DeductionCategory> categories = bill.getLines().stream()
                .map(DeductionLine::getCategory).collect(java.util.stream.Collectors.toSet());
        AuditContext context = new AuditContext(
                tenancy.getState(), tenancy.getFlatSize(), tenancy.getCity(),
                tenancy.getRent(), tenancy.getDeposit(), tenancyMonths(tenancy),
                agreements.termsFor(tenancy.getId()).orElse(null), // null = no agreement read yet
                vault.knownBills(tenancy.getId()),
                prices.statsFor(tenancy.getCity(), tenancy.getFlatSize(), categories));
        RuleSet rules = ruleCatalog.ruleSetFor(tenancy.getState());

        Map<UUID, List<LineInput.Evidence>> linked = evidence.evidenceFor(
                bill.getLines().stream().map(DeductionLine::getId).toList());
        List<LineInput> lines = bill.getLines().stream()
                .map(l -> new LineInput(l.getId(), l.getItem(), l.getCategory(), l.getAmount(), l.getRoom(),
                        linked.getOrDefault(l.getId(), List.of())))
                .toList();
        // Timed on its own: the PRD target (p95 under 300 ms) is for the rules, not the database
        AuditOutcome outcome = metrics.timeAudit(() -> engine.run(lines, context, rules));

        AuditOutcome.Totals t = outcome.totals();
        Audit audit = new Audit(bill.getId(), rules.version(), rules.coverage(), context.tenancyMonths(),
                t.claimed(), t.fair(), t.questionable(), t.needsInfo(), t.suggestedRefund());
        outcome.verdicts().forEach(v ->
                audit.addVerdict(v.line().lineId(), v.verdict(), v.ruleRefs(), v.reason(), v.evidenceIds()));
        Audit saved = audits.saveAndFlush(audit);
        bill.markAudited();

        return toResponse(saved, bill);
    }

    @Transactional(readOnly = true)
    public AuditResponse get(UUID userId, UUID auditId) {
        Audit audit = audits.findOwned(auditId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Audit not found"));
        return toResponse(audit, billService.findOwned(userId, audit.getBillId()));
    }

    /** Whole months lived: to the end date if set, otherwise to today. */
    int tenancyMonths(Tenancy tenancy) {
        LocalDate end = tenancy.getEndDate() != null ? tenancy.getEndDate() : LocalDate.now(clock);
        return (int) Math.max(0, ChronoUnit.MONTHS.between(tenancy.getStartDate(), end));
    }

    private static AuditResponse toResponse(Audit audit, DeductionBill bill) {
        Map<UUID, DeductionLine> linesById = bill.getLines().stream()
                .collect(Collectors.toMap(DeductionLine::getId, Function.identity()));

        List<AuditResponse.LineVerdict> verdicts = audit.getVerdicts().stream()
                .map(v -> toLineVerdict(v, linesById.get(v.getLineId())))
                .sorted(java.util.Comparator.comparingInt(AuditResponse.LineVerdict::position))
                .toList();

        String disclaimer = audit.getRulesCoverage() == RulesCoverage.FULL
                ? DISCLAIMER
                : DISCLAIMER + GENERAL_GUIDANCE_NOTE;

        return new AuditResponse(audit.getId(), audit.getBillId(), audit.getRulesVersion(),
                audit.getRulesCoverage(), audit.getTenancyMonths(),
                new AuditResponse.Totals(audit.getTotalClaimed(), audit.getTotalFair(),
                        audit.getTotalQuestionable(), audit.getTotalNeedsInfo(), audit.getSuggestedRefund()),
                verdicts, disclaimer, audit.getCreatedAt());
    }

    private static AuditResponse.LineVerdict toLineVerdict(AuditVerdict v, DeductionLine line) {
        return new AuditResponse.LineVerdict(v.getLineId(), line.getLineNo(), line.getItem(),
                line.getCategory(), line.getAmount(), v.getVerdict(), v.getRuleIds(), v.getReason(),
                v.getEvidenceIds());
    }
}
