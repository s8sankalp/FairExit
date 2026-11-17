package com.sankalp.fairexit.service.reminders;

import com.sankalp.fairexit.config.ReminderProperties;
import com.sankalp.fairexit.dto.ReminderResponse;
import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderStatus;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.repository.ReminderRepository;
import com.sankalp.fairexit.service.AgreementService;
import com.sankalp.fairexit.service.TenancyService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReminderService {

    private final ReminderRepository reminders;
    private final TenancyService tenancyService;
    private final AgreementService agreements;
    private final ReminderQueue queue;
    private final ReminderProperties properties;
    private final Clock clock;

    public ReminderService(ReminderRepository reminders, TenancyService tenancyService, AgreementService agreements,
                           ReminderQueue queue, ReminderProperties properties, Clock clock) {
        this.reminders = reminders;
        this.tenancyService = tenancyService;
        this.agreements = agreements;
        this.queue = queue;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * (Re)plans the tenancy's reminders from its dates and agreement. Unsent ones are
     * cancelled and planned again, so it is safe to call after any change of dates.
     * A reminder already sent for the same event is kept and never sent a second time.
     */
    @Transactional
    public List<ReminderResponse> schedule(UUID userId, UUID tenancyId) {
        Tenancy tenancy = tenancyService.findOwned(userId, tenancyId);
        if (tenancy.getEndDate() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Add the tenancy's end date (move out day) before setting reminders");
        }
        reminders.cancelPending(tenancy.getId());
        int refundDays = agreements.refundDays(tenancy.getId()).orElse(properties.defaultRefundDays());
        Instant now = clock.instant();

        // A reminder already sent for the same event is never planned again
        java.util.Set<String> alreadySent = reminders.findByTenancyIdOrderByDueAtAsc(tenancy.getId()).stream()
                .filter(r -> r.getStatus() == ReminderStatus.SENT)
                .map(r -> r.getType() + "@" + r.getEventDate())
                .collect(java.util.stream.Collectors.toSet());
        List<Reminder> planned = ReminderPlanner.plan(tenancy.getEndDate(), tenancy.getNoticeDays(), refundDays,
                        properties.sendHour(), now).stream()
                .filter(p -> !alreadySent.contains(p.type() + "@" + p.eventDate()))
                .map(p -> new Reminder(tenancy.getId(), p.type(), p.eventDate(), p.dueAt()))
                .toList();
        List<Reminder> saved = reminders.saveAll(planned);
        // Into Redis only after the rows are committed, so the dispatcher never sees an id it cannot load.
        // If this step is lost, the 10 minute rebuild adds them anyway.
        afterCommit(() -> saved.forEach(r -> queue.add(r.getId(), r.getDueAt())));
        return list(tenancy.getId());
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> list(UUID userId, UUID tenancyId) {
        return list(tenancyService.findOwned(userId, tenancyId).getId());
    }

    /** Stops unsent reminders, for example once the outcome is recorded. */
    @Transactional
    public int cancelPending(UUID tenancyId) {
        return reminders.cancelPending(tenancyId);
    }

    private List<ReminderResponse> list(UUID tenancyId) {
        return reminders.findByTenancyIdOrderByDueAtAsc(tenancyId).stream()
                .filter(r -> r.getStatus() != ReminderStatus.CANCELLED)
                .map(ReminderResponse::from)
                .toList();
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
