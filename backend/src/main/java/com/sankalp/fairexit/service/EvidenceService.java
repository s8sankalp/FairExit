package com.sankalp.fairexit.service;

import com.sankalp.fairexit.dto.EvidenceLinkRequest;
import com.sankalp.fairexit.dto.EvidenceLinkResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.DeductionBill;
import com.sankalp.fairexit.model.DeductionLine;
import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.LineEvidence;
import com.sankalp.fairexit.repository.DeductionBillRepository;
import com.sankalp.fairexit.repository.DocumentRepository;
import com.sankalp.fairexit.repository.LineEvidenceRepository;
import com.sankalp.fairexit.service.audit.LineInput;
import com.sankalp.fairexit.service.vault.VaultService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F8: the tenant links vault photos to a bill line and says what each one shows. */
@Service
public class EvidenceService {

    private final DeductionBillRepository bills;
    private final LineEvidenceRepository links;
    private final DocumentRepository documents;
    private final VaultService vault;

    public EvidenceService(DeductionBillRepository bills, LineEvidenceRepository links,
                           DocumentRepository documents, VaultService vault) {
        this.bills = bills;
        this.links = links;
        this.documents = documents;
        this.vault = vault;
    }

    @Transactional
    public EvidenceLinkResponse link(UUID userId, UUID billId, UUID lineId, EvidenceLinkRequest request) {
        DeductionBill bill = bills.findOwnedForUpdate(billId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bill not found"));
        if (!bill.linesCanChange()) {
            throw new ApiException(HttpStatus.CONFLICT, "This bill has been audited, so its evidence is frozen");
        }
        DeductionLine line = bill.getLines().stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Line not found"));

        List<Document> photos = new ArrayList<>();
        HashSet<UUID> seen = new HashSet<>();
        for (EvidenceLinkRequest.Link link : request.links()) {
            if (!seen.add(link.documentId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A photo can be linked only once per line");
            }
            // Must be a stored photo of this same tenancy, taken at move in or move out
            Document photo = vault.findStored(bill.getTenancyId(), link.documentId(), DocumentType.PHOTO);
            if (photo.getPhase() == DocumentPhase.OTHER) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Only MOVE_IN or MOVE_OUT photos can be used as evidence");
            }
            photos.add(photo);
        }

        links.deleteByLine(line.getId());
        for (int i = 0; i < photos.size(); i++) {
            links.save(new LineEvidence(line.getId(), photos.get(i).getId(), request.links().get(i).observation()));
        }
        return response(line.getId());
    }

    @Transactional(readOnly = true)
    public EvidenceLinkResponse get(UUID userId, UUID billId, UUID lineId) {
        DeductionBill bill = bills.findOwned(billId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bill not found"));
        bill.getLines().stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Line not found"));
        return response(lineId);
    }

    /** For the audit: each line's linked photos, with the phase taken from the vault record. */
    @Transactional(readOnly = true)
    public Map<UUID, List<LineInput.Evidence>> evidenceFor(Collection<UUID> lineIds) {
        if (lineIds.isEmpty()) {
            return Map.of();
        }
        List<LineEvidence> all = links.findByLineIdIn(lineIds);
        Map<UUID, Document> docs = documents.findAllById(all.stream().map(LineEvidence::getDocumentId).toList())
                .stream().collect(Collectors.toMap(Document::getId, Function.identity()));
        return all.stream().collect(Collectors.groupingBy(LineEvidence::getLineId,
                Collectors.mapping(e -> new LineInput.Evidence(e.getDocumentId(),
                        docs.get(e.getDocumentId()).getPhase(), e.getObservation()), Collectors.toList())));
    }

    private EvidenceLinkResponse response(UUID lineId) {
        List<LineEvidence> current = links.findByLineId(lineId);
        Map<UUID, Document> docs = documents.findAllById(current.stream().map(LineEvidence::getDocumentId).toList())
                .stream().collect(Collectors.toMap(Document::getId, Function.identity()));
        return new EvidenceLinkResponse(lineId, current.stream().map(e -> {
            Document d = docs.get(e.getDocumentId());
            return new EvidenceLinkResponse.Link(d.getId(), d.getPhase(), d.getRoom(), d.getItem(), e.getObservation());
        }).toList());
    }
}
