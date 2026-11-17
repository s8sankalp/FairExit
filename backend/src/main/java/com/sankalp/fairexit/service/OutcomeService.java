package com.sankalp.fairexit.service;

import com.sankalp.fairexit.dto.OutcomeRequest;
import com.sankalp.fairexit.dto.OutcomeResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.DocumentType;
import com.sankalp.fairexit.model.Outcome;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.OutcomeRepository;
import com.sankalp.fairexit.service.reminders.ReminderService;
import com.sankalp.fairexit.service.vault.VaultService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F12: what the tenant finally got back. Recording it also stops the unsent reminders. */
@Service
public class OutcomeService {

    private final OutcomeRepository outcomes;
    private final TenancyService tenancyService;
    private final VaultService vault;
    private final ReminderService reminders;
    private final Clock clock;

    public OutcomeService(OutcomeRepository outcomes, TenancyService tenancyService, VaultService vault,
                          ReminderService reminders, Clock clock) {
        this.outcomes = outcomes;
        this.tenancyService = tenancyService;
        this.vault = vault;
        this.reminders = reminders;
        this.clock = clock;
    }

    @Transactional
    public OutcomeResponse record(UUID userId, UUID tenancyId, OutcomeRequest request) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        if (request.amountReturned().compareTo(tenancy.getDeposit()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amountReturned cannot be more than the deposit");
        }
        if (request.receiptDocumentId() != null) {
            vault.findStored(tenancy.getId(), request.receiptDocumentId(), DocumentType.RECEIPT);
        }
        Outcome outcome = outcomes.findByTenancyId(tenancy.getId())
                .orElseGet(() -> new Outcome(tenancy.getId(), clock.instant()));
        outcome.record(request.amountReturned(), request.daysTaken(), request.receiptDocumentId(), clock.instant());
        Outcome saved = outcomes.save(outcome);
        int cancelled = reminders.cancelPending(tenancy.getId());
        return OutcomeResponse.from(saved, tenancy.getDeposit(), cancelled);
    }

    @Transactional(readOnly = true)
    public OutcomeResponse get(UUID userId, UUID tenancyId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        return outcomes.findByTenancyId(tenancy.getId())
                .map(o -> OutcomeResponse.from(o, tenancy.getDeposit(), 0))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No outcome recorded yet"));
    }
}
