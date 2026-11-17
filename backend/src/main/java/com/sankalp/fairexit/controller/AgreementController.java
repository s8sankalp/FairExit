package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.AgreementRequest;
import com.sankalp.fairexit.dto.AgreementResponse;
import com.sankalp.fairexit.service.AgreementService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenancies/{tenancyId}/agreement")
public class AgreementController {

    private final AgreementService agreements;

    public AgreementController(AgreementService agreements) {
        this.agreements = agreements;
    }

    /** 202: read in the background; poll GET until the status is EXTRACTED or FAILED. */
    @PostMapping
    public ResponseEntity<AgreementResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable UUID tenancyId,
                                                   @Valid @RequestBody AgreementRequest request) {
        AgreementResponse agreement = agreements.start(user.id(), tenancyId, request.documentId());
        return ResponseEntity.accepted().location(URI.create("/api/tenancies/" + tenancyId + "/agreement"))
                .body(agreement);
    }

    @GetMapping
    public AgreementResponse latest(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId) {
        return agreements.latest(user.id(), tenancyId);
    }
}
