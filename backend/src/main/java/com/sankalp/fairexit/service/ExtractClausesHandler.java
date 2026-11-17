package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.FairExitMetrics;
import com.sankalp.fairexit.model.Agreement;
import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.model.QuoteSource;
import com.sankalp.fairexit.service.jobs.JobHandler;
import com.sankalp.fairexit.service.jobs.PermanentJobException;
import com.sankalp.fairexit.service.llm.ClauseExtraction;
import com.sankalp.fairexit.service.llm.ClauseExtractor;
import com.sankalp.fairexit.service.llm.LlmException;
import com.sankalp.fairexit.service.vault.VaultService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads an agreement in the background. Does nothing unless it is still EXTRACTING. */
@Component
public class ExtractClausesHandler implements JobHandler {

    static final String COULD_NOT_READ = "We could not read this agreement automatically.";
    static final String DAILY_LIMIT = "You have used today's automatic readings. Please try again tomorrow.";

    private final AgreementService agreements;
    private final VaultService vault;
    private final DocumentReader reader;
    private final ClauseExtractor extractor;
    private final FairExitMetrics metrics;

    public ExtractClausesHandler(AgreementService agreements, VaultService vault, DocumentReader reader,
                                 ClauseExtractor extractor, FairExitMetrics metrics) {
        this.agreements = agreements;
        this.vault = vault;
        this.reader = reader;
        this.extractor = extractor;
        this.metrics = metrics;
    }

    @Override
    public JobType type() {
        return JobType.EXTRACT_CLAUSES;
    }

    @Override
    public void handle(Job job) {
        UUID agreementId = UUID.fromString(job.getPayload().get(AgreementService.PAYLOAD_AGREEMENT_ID));
        UUID userId = UUID.fromString(job.getPayload().get(AgreementService.PAYLOAD_USER_ID));
        Optional<Agreement> agreement = agreements.awaitingExtraction(agreementId);
        if (agreement.isEmpty()) {
            return;
        }
        Document document = vault.findById(agreement.get().getDocumentId());
        DocumentReader.Content content = reader.read(document, userId);
        try {
            if (content.isText()) {
                // Quotes are checked against the PDF's own text: the strongest proof
                ClauseExtraction result = extractor.fromText(userId, content.maskedPages());
                agreements.applyExtraction(agreementId, result, content.maskedPages(), QuoteSource.TEXT_LAYER);
            } else {
                ClauseExtraction result = extractor.fromFile(userId, content.sha256(), content.mimeType(),
                        content.fileBytes());
                agreements.applyExtraction(agreementId, result, result.pages(), QuoteSource.TRANSCRIPTION);
            }
        } catch (LlmException e) {
            if (e.retryable()) {
                throw e;
            }
            throw new PermanentJobException(
                    e.kind() == LlmException.Kind.QUOTA_EXCEEDED ? DAILY_LIMIT : COULD_NOT_READ);
        }
    }

    @Override
    public void onGiveUp(Job job, Exception cause) {
        UUID agreementId = UUID.fromString(job.getPayload().get(AgreementService.PAYLOAD_AGREEMENT_ID));
        agreements.markFailed(agreementId,
                cause instanceof PermanentJobException ? cause.getMessage() : COULD_NOT_READ);
        metrics.extractionFailed("clauses");
    }
}
