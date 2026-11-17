package com.sankalp.fairexit.service.vault;

import com.sankalp.fairexit.dto.CreateUploadRequest;
import com.sankalp.fairexit.dto.DocumentResponse;
import com.sankalp.fairexit.dto.DownloadLink;
import com.sankalp.fairexit.dto.UploadTicket;
import com.sankalp.fairexit.dto.VerifyResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.DocumentStatus;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.DocumentRepository;
import com.sankalp.fairexit.repository.TenancyRepository;
import com.sankalp.fairexit.service.Hashing;
import com.sankalp.fairexit.service.TenancyService;
import com.sankalp.fairexit.service.audit.AuditContext.KnownBill;
import com.sankalp.fairexit.service.storage.ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The Evidence Vault.
 *
 * Upload in two steps, so file bytes never pass through the app on the way in:
 *  1. requestUpload: the app hands out a presigned URL for a staging key (10 minutes).
 *  2. complete: the app reads the staged file, checks its real type and size, hashes it,
 *     writes those exact bytes to a vault key that no client has a URL for, and links the
 *     record into the tenancy's hash chain.
 */
@Service
public class VaultService {

    private final DocumentRepository documents;
    private final TenancyRepository tenancies;
    private final TenancyService tenancyService;
    private final ObjectStorage storage;
    private final Clock clock;

    public VaultService(DocumentRepository documents, TenancyRepository tenancies, TenancyService tenancyService,
                        ObjectStorage storage, Clock clock) {
        this.documents = documents;
        this.tenancies = tenancies;
        this.tenancyService = tenancyService;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public UploadTicket requestUpload(UUID userId, UUID tenancyId, CreateUploadRequest request) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        if (!FileTypes.ALLOWED.contains(request.contentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPG, PNG and PDF files are allowed");
        }
        if (request.sizeBytes() > storage.maxUploadBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Files can be at most 10 MB");
        }
        UUID id = UUID.randomUUID();
        Document document = new Document(id, tenancy.getId(), request.type(),
                request.phase() == null ? DocumentPhase.OTHER : request.phase(),
                request.fileName().trim(), request.contentType(), request.sizeBytes(),
                blankToNull(request.room()), blankToNull(request.item()), request.amount(),
                stagingKey(tenancy.getId(), id));
        documents.save(document);

        Instant expiresAt = clock.instant().plus(storage.urlTtl());
        String url = storage.presignUpload(document.getStorageKey(), request.contentType(), request.sizeBytes())
                .toString();
        return new UploadTicket(id, "PUT", url, Map.of("Content-Type", request.contentType()), expiresAt);
    }

    /**
     * Safe to call twice: a stored document is returned as it is.
     * The tenancy row is locked, so two completes for one tenancy chain one after the other.
     */
    // A rejected file must stay REJECTED, so throwing the 422 must not roll the change back
    @Transactional(noRollbackFor = ApiException.class)
    public DocumentResponse complete(UUID userId, UUID tenancyId, UUID documentId) {
        Tenancy tenancy = tenancies.findOwnedForUpdate(tenancyId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenancy not found"));
        Document document = documents.findByIdAndTenancyId(documentId, tenancy.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (document.isStored()) {
            return DocumentResponse.from(document);
        }
        if (document.getStatus() == DocumentStatus.REJECTED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, document.getRejectReason());
        }

        String stagingKey = document.getStorageKey();
        byte[] bytes = storage.read(stagingKey)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "The file has not been uploaded yet"));
        String problem = check(document, bytes);
        if (problem != null) {
            document.reject(problem);
            deleteAfterCommit(stagingKey);
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, problem);
        }

        // Hash the bytes we hold and store exactly those bytes. Copying the staging object
        // instead would let a client overwrite it in between, with the URL still valid.
        String sha256 = Hashing.sha256Hex(bytes);
        String vaultKey = vaultKey(tenancy.getId(), document.getId());
        storage.write(vaultKey, bytes, document.getContentType());

        // Postgres keeps microseconds; cut to milliseconds so the hash can be recomputed from the row
        Instant uploadedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String prevHash = tenancy.getChainHead() == null ? ChainHash.GENESIS : tenancy.getChainHead();
        int seq = tenancy.getChainLength() + 1;
        String recordHash = ChainHash.of(fields(document, seq, prevHash, sha256, uploadedAt));

        document.markStored(vaultKey, sha256, seq, prevHash, recordHash, uploadedAt);
        tenancy.appendToChain(recordHash);
        deleteAfterCommit(stagingKey);
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID userId, UUID tenancyId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        return documents.findByTenancyIdAndStatusOrderByChainSeqAsc(tenancy.getId(), DocumentStatus.STORED)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DownloadLink download(UUID userId, UUID documentId) {
        Document document = documents.findOwned(documentId, userId)
                .filter(Document::isStored)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        return new DownloadLink(storage.presignDownload(document.getStorageKey()).toString(),
                clock.instant().plus(storage.urlTtl()));
    }

    /** Re-reads every file, rehashes it and walks the chain. */
    @Transactional(readOnly = true)
    public VerifyResponse verify(UUID userId, UUID tenancyId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        List<Document> chain = documents.findByTenancyIdAndStatusOrderByChainSeqAsc(tenancy.getId(),
                DocumentStatus.STORED);

        Map<UUID, String> currentHashes = new HashMap<>();
        for (Document d : chain) {
            storage.read(d.getStorageKey()).ifPresent(bytes -> currentHashes.put(d.getId(), Hashing.sha256Hex(bytes)));
        }
        List<ChainVerifier.Record> records = chain.stream()
                .map(d -> new ChainVerifier.Record(
                        fields(d, d.getChainSeq(), d.getPrevHash(), d.getSha256(), d.getUploadedAt()),
                        d.getRecordHash()))
                .toList();
        List<ChainVerifier.Break> breaks = ChainVerifier.verify(records, currentHashes,
                tenancy.getChainLength(), tenancy.getChainHead());
        return new VerifyResponse(tenancy.getId(), breaks.isEmpty(), chain.size(), tenancy.getChainHead(),
                breaks, clock.instant());
    }

    /** A stored document of this tenancy, for bills and agreements. */
    @Transactional(readOnly = true)
    public Document findStored(UUID tenancyId, UUID documentId, DocumentType expectedType) {
        Document document = documents.findByIdAndTenancyId(documentId, tenancyId)
                .filter(Document::isStored)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (document.getType() != expectedType) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Expected a document of type " + expectedType + ", got " + document.getType());
        }
        return document;
    }

    @Transactional(readOnly = true)
    public Document findById(UUID documentId) {
        return documents.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document " + documentId + " is gone"));
    }

    /** Utility bills on file, for audit rule 1. */
    @Transactional(readOnly = true)
    public List<KnownBill> knownBills(UUID tenancyId) {
        return documents.findByTenancyIdAndStatusAndType(tenancyId, DocumentStatus.STORED, DocumentType.UTILITY_BILL)
                .stream()
                .filter(d -> d.getAmount() != null)
                .map(d -> new KnownBill(DeductionCategory.UTILITY, d.getAmount()))
                .toList();
    }

    /** The stored bytes of a document, for the extraction jobs. */
    public byte[] contentOf(Document document) {
        return storage.read(document.getStorageKey())
                .orElseThrow(() -> new IllegalStateException("File for document " + document.getId() + " is missing"));
    }

    private String check(Document document, byte[] bytes) {
        if (bytes.length > storage.maxUploadBytes()) {
            return "Files can be at most 10 MB";
        }
        if (bytes.length != document.getSizeBytes()) {
            return "The uploaded file is not the size that was declared";
        }
        String actual = FileTypes.sniff(bytes).orElse(null);
        if (!document.getContentType().equals(actual)) {
            return "The file is not a real " + document.getContentType() + " file";
        }
        return null;
    }

    private static ChainHash.Fields fields(Document d, int seq, String prevHash, String sha256, Instant uploadedAt) {
        return new ChainHash.Fields(prevHash, seq, d.getId(), d.getTenancyId(), d.getType().name(),
                d.getPhase().name(), d.getRoom(), d.getItem(), d.getAmount(), sha256, d.getSizeBytes(), uploadedAt);
    }

    /** Only after the database commit, so a failed commit can be retried with the file still there. */
    private void deleteAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.deleteQuietly(key);
                }
            });
        } else {
            storage.deleteQuietly(key);
        }
    }

    static String stagingKey(UUID tenancyId, UUID documentId) {
        return "staging/" + tenancyId + "/" + documentId;
    }

    static String vaultKey(UUID tenancyId, UUID documentId) {
        return "vault/" + tenancyId + "/" + documentId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
