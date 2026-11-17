package com.sankalp.fairexit.controller;

import com.sankalp.fairexit.config.AuthenticatedUser;
import com.sankalp.fairexit.dto.ReminderResponse;
import com.sankalp.fairexit.service.reminders.ReminderService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenancies/{tenancyId}/reminders")
public class ReminderController {

    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    /** Plans (or re-plans) the notice, move out and refund reminders. */
    @PostMapping
    public List<ReminderResponse> schedule(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID tenancyId) {
        return reminders.schedule(user.id(), tenancyId);
    }

    @GetMapping
    public List<ReminderResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tenancyId) {
        return reminders.list(user.id(), tenancyId);
    }
}
