package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.CreateTenancyRequest;
import com.sankalp.fairexit.dto.TenancyResponse;
import com.sankalp.fairexit.service.TenancyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/tenancies")
public class TenancyController {

    private final TenancyService tenancyService;

    public TenancyController(TenancyService tenancyService) {
        this.tenancyService = tenancyService;
    }

    @PostMapping
    public ResponseEntity<TenancyResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody CreateTenancyRequest request) {
        TenancyResponse created = tenancyService.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/tenancies/" + created.id())).body(created);
    }

    /** A user has only a handful of homes, so a plain list is enough; no paging. */
    @GetMapping
    public List<TenancyResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return tenancyService.list(user.id());
    }

    @GetMapping("/{id}")
    public TenancyResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return tenancyService.get(user.id(), id);
    }
}
