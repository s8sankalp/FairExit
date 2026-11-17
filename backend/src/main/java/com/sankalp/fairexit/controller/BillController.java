package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.dto.BillResponse;
import com.sankalp.fairexit.dto.BillSummary;
import java.util.List;
import com.sankalp.fairexit.dto.ConfirmLinesRequest;
import com.sankalp.fairexit.dto.CreateBillRequest;
import com.sankalp.fairexit.dto.EvidenceLinkRequest;
import com.sankalp.fairexit.dto.EvidenceLinkResponse;
import com.sankalp.fairexit.service.AuditService;
import com.sankalp.fairexit.service.BillService;
import com.sankalp.fairexit.service.EvidenceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BillController {

    private final BillService billService;
    private final AuditService auditService;
    private final EvidenceService evidenceService;

    public BillController(BillService billService, AuditService auditService, EvidenceService evidenceService) {
        this.billService = billService;
        this.auditService = auditService;
        this.evidenceService = evidenceService;
    }

    @PostMapping("/tenancies/{tenancyId}/bills")
    public ResponseEntity<BillResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID tenancyId,
                                               @Valid @RequestBody CreateBillRequest request) {
        URI location;
        if (request.hasDocument()) {
            BillResponse bill = billService.createFromDocument(user.id(), tenancyId, request.documentId());
            location = URI.create("/api/bills/" + bill.id());
            return ResponseEntity.accepted().location(location).body(bill);
        }
        if (request.hasText()) {
            // 202 Accepted: the AI reads the text in the background; poll GET /api/bills/{id}
            BillResponse bill = billService.createFromText(user.id(), tenancyId, request.text());
            location = URI.create("/api/bills/" + bill.id());
            return ResponseEntity.accepted().location(location).body(bill);
        }
        BillResponse bill = billService.createManual(user.id(), tenancyId, request);
        location = URI.create("/api/bills/" + bill.id());
        return ResponseEntity.created(location).body(bill);
    }

    @GetMapping("/tenancies/{tenancyId}/bills")
    public List<BillSummary> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId) {
        return billService.list(user.id(), tenancyId);
    }

    /** Confirm, edit, delete or add lines. Replaces the whole list and makes the bill CONFIRMED. */
    @PutMapping("/bills/{billId}/lines")
    public BillResponse confirmLines(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable UUID billId,
                                     @Valid @RequestBody ConfirmLinesRequest request) {
        return billService.confirmLines(user.id(), billId, request);
    }

    /** Link move in and move out photos to a line (rules 4 and 6 read them). Replaces the line's links. */
    @PutMapping("/bills/{billId}/lines/{lineId}/evidence")
    public EvidenceLinkResponse linkEvidence(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID billId, @PathVariable UUID lineId,
                                             @Valid @RequestBody EvidenceLinkRequest request) {
        return evidenceService.link(user.id(), billId, lineId, request);
    }

    @GetMapping("/bills/{billId}/lines/{lineId}/evidence")
    public EvidenceLinkResponse evidence(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID billId, @PathVariable UUID lineId) {
        return evidenceService.get(user.id(), billId, lineId);
    }

    @GetMapping("/bills/{billId}")
    public BillResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID billId) {
        return billService.get(user.id(), billId);
    }

    @PostMapping("/bills/{billId}/audit")
    public ResponseEntity<AuditResponse> audit(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID billId) {
        AuditResponse audit = auditService.run(user.id(), billId);
        return ResponseEntity.created(URI.create("/api/audits/" + audit.id())).body(audit);
    }

    @GetMapping("/audits/{auditId}")
    public AuditResponse getAudit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID auditId) {
        return auditService.get(user.id(), auditId);
    }
}
