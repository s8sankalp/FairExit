package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderStatus;
import com.sankalp.fairexit.model.ReminderType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReminderResponse(UUID id, ReminderType type, LocalDate eventDate, Instant dueAt, String channel,
                               ReminderStatus status, Instant sentAt) {

    public static ReminderResponse from(Reminder r) {
        return new ReminderResponse(r.getId(), r.getType(), r.getEventDate(), r.getDueAt(), r.getChannel(),
                r.getStatus(), r.getSentAt());
    }
}
