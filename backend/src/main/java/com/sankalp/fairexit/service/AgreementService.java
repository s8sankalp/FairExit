package com.sankalp.fairexit.service;

import com.sankalp.fairexit.dto.AgreementResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.Agreement;
import com.sankalp.fairexit.model.AgreementStatus;
import com.sankalp.fairexit.model.Clause;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.model.QuoteSource;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.AgreementRepository;
import com.sankalp.fairexit.service.audit.AuditContext.AgreementTerms;
import com.sankalp.fairexit.service.jobs.JobService;
import com.sankalp.fairexit.service.llm.ClauseExtraction;
import com.sankalp.fairexit.service.llm.QuoteChecker;
import com.sankalp.fairexit.service.vault.VaultService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgreementService {

    public static final String PAYLOAD_AGREEMENT_ID = "agreementId";
    public static final String PAYLOAD_USER_ID = "userId";

    private final AgreementRepository agreements;
    private final TenancyService tenancyService;
    private final VaultService vault;
    private final JobService jobService;

    public AgreementService(AgreementRepository agreements, TenancyService tenancyService, VaultService vault,
                            JobService jobService) {
        this.agreements = agreements;
        this.tenancyService = tenancyService;
        this.vault = vault;
        this.jobService = jobService;
    }

    /** Queues the reading of a stored agreement document. The row and the job commit together. */
    @Transactional
    public AgreementResponse start(UUID userId, UUID tenancyId, UUID documentId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        vault.findStored(tenancy.getId(), documentId, DocumentType.AGREEMENT);
        Agreement agreement = agreements.saveAndFlush(new Agreement(tenancy.getId(), documentId));
        jobService.enqueue(JobType.EXTRACT_CLAUSES, Map.of(
                PAYLOAD_AGREEMENT_ID, agreement.getId().toString(),
                PAYLOAD_USER_ID, userId.toString()));
        return AgreementResponse.from(agreement);
    }

    @Transactional(readOnly = true)
    public AgreementResponse latest(UUID userId, UUID tenancyId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        return agreements.findFirstByTenancyIdOrderByCreatedAtDesc(tenancy.getId())
                .map(AgreementResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No agreement uploaded yet"));
    }

    /**
     * What the audit may rely on. Empty means "no agreement read yet", which the rules treat
     * very differently from an agreement with no clause about a charge.
     */
    @Transactional(readOnly = true)
    public Optional<AgreementTerms> termsFor(UUID tenancyId) {
        return agreements.findFirstByTenancyIdAndStatusOrderByCreatedAtDesc(tenancyId, AgreementStatus.EXTRACTED)
                .map(a -> {
                    Set<DeductionCategory> tenantPays = a.getClauses().stream()
                            .filter(Clause::tenantPays)
                            .map(c -> c.getType().chargeCategory())
                            .collect(Collectors.toSet());
                    return new AgreementTerms(tenantPays);
                });
    }

    /** Days the agreement gives the landlord to return the deposit, if a clause says so. */
    @Transactional(readOnly = true)
    public Optional<Integer> refundDays(UUID tenancyId) {
        return agreements.findFirstByTenancyIdAndStatusOrderByCreatedAtDesc(tenancyId, AgreementStatus.EXTRACTED)
                .flatMap(a -> a.getClauses().stream()
                        .filter(c -> c.getType() == com.sankalp.fairexit.model.ClauseType.REFUND_TIMELINE)
                        .map(c -> c.getValue().get("days"))
                        .filter(Number.class::isInstance)
                        .map(n -> ((Number) n).intValue())
                        .filter(d -> d > 0 && d <= 365)
                        .findFirst());
    }

    // ---- Used by the background job ----

    @Transactional(readOnly = true)
    public Optional<Agreement> awaitingExtraction(UUID agreementId) {
        return agreements.findById(agreementId).filter(Agreement::isExtracting);
    }

    /**
     * Keeps only clauses whose quote is really in the source; the rest are counted and dropped.
     *
     * @param sourcePages the PDF's own text (TEXT_LAYER) or the model's transcription (TRANSCRIPTION)
     */
    @Transactional
    public void applyExtraction(UUID agreementId, ClauseExtraction extraction, List<String> sourcePages,
                                QuoteSource source) {
        agreements.findByIdForUpdate(agreementId).filter(Agreement::isExtracting).ifPresent(agreement -> {
            int dropped = 0;
            for (ClauseExtraction.RawClause c : extraction.clauses()) {
                if (QuoteChecker.isInSource(c.quote(), c.page(), sourcePages)) {
                    agreement.addClause(c.type(), c.page(), c.quote(), c.value(), source);
                } else {
                    dropped++;
                }
            }
            agreement.markExtracted(dropped);
        });
    }

    @Transactional
    public void markFailed(UUID agreementId, String message) {
        agreements.findByIdForUpdate(agreementId).filter(Agreement::isExtracting)
                .ifPresent(a -> a.markFailed(message));
    }
}
