package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.OutcomeRequest;
import com.sankalp.fairexit.dto.OutcomeResponse;
import com.sankalp.fairexit.service.OutcomeService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenancies/{tenancyId}/outcome")
public class OutcomeController {

    private final OutcomeService outcomes;

    public OutcomeController(OutcomeService outcomes) {
        this.outcomes = outcomes;
    }

    /** PRD path: POST /tenancies/{id}/outcome. Sending it again updates the same outcome. */
    @PostMapping
    public OutcomeResponse record(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId,
                                  @Valid @RequestBody OutcomeRequest request) {
        return outcomes.record(user.id(), tenancyId, request);
    }

    @GetMapping
    public OutcomeResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId) {
        return outcomes.get(user.id(), tenancyId);
    }
}
