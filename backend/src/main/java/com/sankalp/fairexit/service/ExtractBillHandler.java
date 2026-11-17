package com.sankalp.fairexit.service;

import com.sankalp.fairexit.config.FairExitMetrics;
import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.service.jobs.JobHandler;
import com.sankalp.fairexit.service.jobs.PermanentJobException;
import com.sankalp.fairexit.service.llm.BillExtractor;
import com.sankalp.fairexit.service.llm.ExtractionResult;
import com.sankalp.fairexit.service.llm.LlmException;
import com.sankalp.fairexit.service.vault.VaultService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The job that reads a pasted bill. Safe to run twice: it does nothing unless the bill
 * is still EXTRACTING, and the result is written under a row lock with the same check.
 */
@Component
public class ExtractBillHandler implements JobHandler {

    static final String COULD_NOT_READ =
            "We could not read this bill automatically. Please enter the lines by hand.";
    static final String DAILY_LIMIT =
            "You have used today's automatic readings. Please enter the lines by hand.";
    static final String NO_LINES =
            "No deduction lines were found in this text. Please enter the lines by hand.";

    private final BillService billService;
    private final BillExtractor extractor;
    private final VaultService vault;
    private final DocumentReader reader;
    private final FairExitMetrics metrics;

    public ExtractBillHandler(BillService billService, BillExtractor extractor, VaultService vault,
                              DocumentReader reader, FairExitMetrics metrics) {
        this.billService = billService;
        this.extractor = extractor;
        this.vault = vault;
        this.reader = reader;
        this.metrics = metrics;
    }

    @Override
    public JobType type() {
        return JobType.EXTRACT_BILL;
    }

    @Override
    public void handle(Job job) {
        UUID billId = UUID.fromString(job.getPayload().get(BillService.PAYLOAD_BILL_ID));
        UUID userId = UUID.fromString(job.getPayload().get(BillService.PAYLOAD_USER_ID));

        Optional<BillService.ExtractionSource> source = billService.awaitingExtraction(billId);
        if (source.isEmpty()) {
            return; // deleted, already done, or the tenant typed the lines by hand
        }
        ExtractionResult result;
        try {
            result = read(userId, source.get());
        } catch (LlmException e) {
            if (e.retryable()) {
                throw e; // timeout or bad answer: the queue retries with backoff
            }
            throw new PermanentJobException(userMessage(e));
        }
        billService.applyExtraction(billId, result);
    }

    private ExtractionResult read(UUID userId, BillService.ExtractionSource source) {
        if (source.documentId() == null) {
            return extractor.extract(userId, source.maskedText());
        }
        Document document = vault.findById(source.documentId());
        DocumentReader.Content content = reader.read(document, userId);
        if (content.isText()) {
            return extractor.extract(userId, String.join("\n", content.maskedPages()));
        }
        return extractor.extractFile(userId, content.sha256(), content.mimeType(), content.fileBytes());
    }

    @Override
    public void onGiveUp(Job job, Exception cause) {
        UUID billId = UUID.fromString(job.getPayload().get(BillService.PAYLOAD_BILL_ID));
        String message = cause instanceof PermanentJobException ? cause.getMessage() : COULD_NOT_READ;
        billService.markExtractionFailed(billId, message);
        metrics.extractionFailed("bill");
    }

    private static String userMessage(LlmException e) {
        return switch (e.kind()) {
            case QUOTA_EXCEEDED -> DAILY_LIMIT;
            case NO_LINES -> NO_LINES;
            default -> COULD_NOT_READ;
        };
    }
}
