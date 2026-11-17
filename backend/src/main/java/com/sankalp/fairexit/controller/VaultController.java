package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.CreateUploadRequest;
import com.sankalp.fairexit.dto.DocumentResponse;
import com.sankalp.fairexit.dto.DownloadLink;
import com.sankalp.fairexit.dto.UploadTicket;
import com.sankalp.fairexit.dto.VerifyResponse;
import com.sankalp.fairexit.service.vault.VaultService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No update or delete endpoints, on purpose: the vault is append only. */
@RestController
@RequestMapping("/api")
public class VaultController {

    private final VaultService vault;

    public VaultController(VaultService vault) {
        this.vault = vault;
    }

    @PostMapping("/tenancies/{tenancyId}/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicket requestUpload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId,
                                      @Valid @RequestBody CreateUploadRequest request) {
        return vault.requestUpload(user.id(), tenancyId, request);
    }

    @PostMapping("/tenancies/{tenancyId}/uploads/{documentId}/complete")
    public DocumentResponse complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId,
                                     @PathVariable UUID documentId) {
        return vault.complete(user.id(), tenancyId, documentId);
    }

    @GetMapping("/tenancies/{tenancyId}/documents")
    public List<DocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId) {
        return vault.list(user.id(), tenancyId);
    }

    @GetMapping("/documents/{documentId}/download")
    public DownloadLink download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID documentId) {
        return vault.download(user.id(), documentId);
    }

    @GetMapping("/tenancies/{tenancyId}/verify")
    public VerifyResponse verify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId) {
        return vault.verify(user.id(), tenancyId);
    }
}
