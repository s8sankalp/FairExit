package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.EvidencePackResponse;
import com.sankalp.fairexit.service.pack.EvidencePackService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidencePackController {

    private final EvidencePackService packs;

    public EvidencePackController(EvidencePackService packs) {
        this.packs = packs;
    }

    @PostMapping("/api/audits/{auditId}/evidence-pack")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidencePackResponse build(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID auditId) {
        return packs.build(user.id(), auditId);
    }
}
