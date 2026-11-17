package com.sankalp.fairexit.service;

import com.sankalp.fairexit.dto.BillLineRequest;
import com.sankalp.fairexit.dto.BillResponse;
import com.sankalp.fairexit.dto.BillSummary;
import com.sankalp.fairexit.repository.AuditRepository;
import com.sankalp.fairexit.dto.ConfirmLinesRequest;
import com.sankalp.fairexit.dto.CreateBillRequest;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.DeductionBill;
import com.sankalp.fairexit.model.DeductionLine;
import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.DeductionBillRepository;
import com.sankalp.fairexit.repository.UserRepository;
import com.sankalp.fairexit.service.jobs.JobService;
import com.sankalp.fairexit.service.llm.ExtractionResult;
import com.sankalp.fairexit.service.llm.PiiMasker;
import com.sankalp.fairexit.service.vault.VaultService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillService {

    public static final String PAYLOAD_BILL_ID = "billId";
    public static final String PAYLOAD_USER_ID = "userId";

    private final DeductionBillRepository bills;
    private final TenancyService tenancyService;
    private final UserRepository users;
    private final PiiMasker masker;
    private final JobService jobService;
    private final VaultService vault;
    private final AuditRepository audits;

    public BillService(DeductionBillRepository bills, TenancyService tenancyService, UserRepository users,
                       PiiMasker masker, JobService jobService, VaultService vault, AuditRepository audits) {
        this.bills = bills;
        this.tenancyService = tenancyService;
        this.users = users;
        this.masker = masker;
        this.jobService = jobService;
        this.vault = vault;
        this.audits = audits;
    }

    @Transactional
    public BillResponse createManual(UUID userId, UUID tenancyId, CreateBillRequest request) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        DeductionBill bill = DeductionBill.manual(tenancy.getId());
        for (BillLineRequest line : request.lines()) {
            bill.addLine(line.item().trim(), line.category(), line.amount(), blankToNull(line.room()));
        }
        return BillResponse.from(bills.saveAndFlush(bill));
    }

    /**
     * Stores the pasted message (masked) and queues a job for the AI to read it.
     * The bill and the job are saved in one transaction: either both exist or neither.
     */
    @Transactional
    public BillResponse createFromText(UUID userId, UUID tenancyId, String text) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        List<String> names = users.findById(userId).map(User::getName).map(List::of).orElse(List.of());
        String masked = masker.mask(text.trim(), names);

        DeductionBill bill = bills.saveAndFlush(
                DeductionBill.fromText(tenancy.getId(), masked, Hashing.sha256Hex(masked)));
        enqueueExtraction(bill, userId);
        return BillResponse.from(bill);
    }

    private void enqueueExtraction(DeductionBill bill, UUID userId) {
        jobService.enqueue(JobType.EXTRACT_BILL, Map.of(
                PAYLOAD_BILL_ID, bill.getId().toString(),
                PAYLOAD_USER_ID, userId.toString()));
    }

    /** A photo or PDF of the bill, already in the vault, read by the AI in the background. */
    @Transactional
    public BillResponse createFromDocument(UUID userId, UUID tenancyId, UUID documentId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        Document document = vault.findStored(tenancy.getId(), documentId, DocumentType.BILL);
        DeductionBill bill = bills.saveAndFlush(
                DeductionBill.fromDocument(tenancy.getId(), document.getId(), document.getSha256()));
        enqueueExtraction(bill, userId);
        return BillResponse.from(bill);
    }

    /**
     * The tenant's final say on the lines: confirm, edit, delete or add. Also the manual
     * fallback: it works while the AI is still reading, and after the AI has failed.
     */
    @Transactional
    public BillResponse confirmLines(UUID userId, UUID billId, ConfirmLinesRequest request) {
        DeductionBill bill = bills.findOwnedForUpdate(billId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bill not found"));
        if (!bill.linesCanChange()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This bill has been audited, so its lines are frozen. Create a new bill to change them.");
        }
        List<DeductionLine> suggested = List.copyOf(bill.getLines());

        // Delete the old lines first. Without this flush Hibernate would insert the new
        // lines before deleting the old ones and break the unique (bill, line number) rule.
        bill.clearLines();
        bills.flush();

        for (BillLineRequest line : request.lines()) {
            String item = line.item().trim();
            String room = blankToNull(line.room());
            // "Edited" = the AI suggested lines and this is not one of them.
            // The share of edited lines is our measure of how well the AI reads bills.
            boolean edited = !suggested.isEmpty() && suggested.stream()
                    .noneMatch(s -> s.sameContent(item, line.category(), line.amount(), room));
            bill.addLine(item, line.category(), line.amount(), room, edited);
        }
        bill.markConfirmed();
        return BillResponse.from(bills.saveAndFlush(bill));
    }

    /** A tenancy's bills, newest first, each with its latest audit if there is one. */
    @Transactional(readOnly = true)
    public List<BillSummary> list(UUID userId, UUID tenancyId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        return bills.findByTenancyIdOrderByCreatedAtDesc(tenancy.getId()).stream()
                .map(b -> new BillSummary(b.getId(), b.getStatus(), b.getSource(), b.total(), b.getLines().size(),
                        audits.findFirstByBillIdOrderByCreatedAtDesc(b.getId()).map(a -> a.getId()).orElse(null),
                        b.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BillResponse get(UUID userId, UUID billId) {
        return BillResponse.from(findOwned(userId, billId));
    }

    @Transactional(readOnly = true)
    public DeductionBill findOwned(UUID userId, UUID billId) {
        return bills.findOwned(billId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bill not found"));
    }

    // ---- Used by the background extraction job (acts for the system, not a user) ----

    /** What to read: masked text, or a vault document. Empty unless the bill is still waiting for the AI. */
    public record ExtractionSource(String maskedText, UUID documentId) {
    }

    @Transactional(readOnly = true)
    public Optional<ExtractionSource> awaitingExtraction(UUID billId) {
        return bills.findById(billId)
                .filter(DeductionBill::isExtracting)
                .map(b -> new ExtractionSource(b.getMaskedText(), b.getDocumentId()));
    }

    /** Writes the AI's lines, unless the tenant already took over by typing lines. */
    @Transactional
    public void applyExtraction(UUID billId, ExtractionResult result) {
        bills.findByIdForUpdate(billId).filter(DeductionBill::isExtracting).ifPresent(bill -> {
            result.lines().forEach(l -> bill.addLine(l.item(), l.category(), l.amount(), l.room()));
            bill.markExtracted(result.statedTotal());
        });
    }

    @Transactional
    public void markExtractionFailed(UUID billId, String message) {
        bills.findByIdForUpdate(billId).filter(DeductionBill::isExtracting)
                .ifPresent(bill -> bill.markExtractionFailed(message));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
