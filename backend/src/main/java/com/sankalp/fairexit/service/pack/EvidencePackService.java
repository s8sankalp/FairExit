package com.sankalp.fairexit.service.pack;

import com.sankalp.fairexit.dto.AgreementResponse;
import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.dto.DocumentResponse;
import com.sankalp.fairexit.dto.EvidencePackResponse;
import com.sankalp.fairexit.dto.VerifyResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.EvidencePack;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.EvidencePackRepository;
import com.sankalp.fairexit.repository.UserRepository;
import com.sankalp.fairexit.service.AgreementService;
import com.sankalp.fairexit.service.AuditService;
import com.sankalp.fairexit.service.BillService;
import com.sankalp.fairexit.service.Hashing;
import com.sankalp.fairexit.service.TenancyService;
import com.sankalp.fairexit.service.storage.ObjectStorage;
import com.sankalp.fairexit.service.vault.VaultService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Builds the evidence pack: verifies the vault first (PRD: "hash chain verified on every
 * evidence pack"), draws the PDF, stores it, and returns a link that works for 7 days.
 */
@Service
public class EvidencePackService {

    static final Duration LINK_TTL = Duration.ofDays(7);

    private final AuditService audits;
    private final BillService bills;
    private final TenancyService tenancies;
    private final AgreementService agreements;
    private final VaultService vault;
    private final UserRepository users;
    private final ObjectStorage storage;
    private final EvidencePackRepository packs;
    private final Clock clock;

    public EvidencePackService(AuditService audits, BillService bills, TenancyService tenancies,
                               AgreementService agreements, VaultService vault, UserRepository users,
                               ObjectStorage storage, EvidencePackRepository packs, Clock clock) {
        this.audits = audits;
        this.bills = bills;
        this.tenancies = tenancies;
        this.agreements = agreements;
        this.vault = vault;
        this.users = users;
        this.storage = storage;
        this.packs = packs;
        this.clock = clock;
    }

    public EvidencePackResponse build(UUID userId, UUID auditId) {
        AuditResponse audit = audits.get(userId, auditId); // 404 if not this user's
        UUID tenancyId = bills.findOwned(userId, audit.billId()).getTenancyId();
        Tenancy tenancy = tenancies.findOwned(userId, tenancyId);

        VerifyResponse verify = vault.verify(userId, tenancyId);
        List<DocumentResponse> documents = vault.list(userId, tenancyId);
        Map<UUID, byte[]> photos = new HashMap<>();
        documents.stream()
                .filter(d -> d.type() == DocumentType.PHOTO)
                .limit(EvidencePackPdf.MAX_PHOTOS)
                .forEach(d -> photos.put(d.id(), vault.contentOf(vault.findById(d.id()))));
        AgreementResponse agreement = latestAgreement(userId, tenancyId);
        String name = users.findById(userId).map(User::getName).orElse(null);

        Instant now = clock.instant();
        byte[] pdf = EvidencePackPdf.build(
                new PackContent(name, tenancy, audit, agreement, documents, photos, verify, now));

        UUID packId = UUID.randomUUID();
        String key = "packs/" + tenancyId + "/" + packId + ".pdf";
        storage.write(key, pdf, "application/pdf");
        String sha256 = Hashing.sha256Hex(pdf);
        Instant expiresAt = now.plus(LINK_TTL);
        packs.save(new EvidencePack(packId, auditId, tenancyId, key, sha256, verify.chainHead(), verify.valid(),
                now, expiresAt));

        return new EvidencePackResponse(packId, storage.presignDownload(key, LINK_TTL).toString(), expiresAt, sha256,
                verify.valid());
    }

    private AgreementResponse latestAgreement(UUID userId, UUID tenancyId) {
        try {
            return agreements.latest(userId, tenancyId);
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }
}
