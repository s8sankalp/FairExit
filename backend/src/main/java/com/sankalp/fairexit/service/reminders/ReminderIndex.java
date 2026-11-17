package com.sankalp.fairexit.service.reminders;

import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderStatus;
import com.sankalp.fairexit.repository.ReminderRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Puts every PENDING reminder (back) into the Redis set. Idempotent, so it can run any time. */
@Component
public class ReminderIndex {

    private final ReminderRepository reminders;
    private final ReminderQueue queue;

    public ReminderIndex(ReminderRepository reminders, ReminderQueue queue) {
        this.reminders = reminders;
        this.queue = queue;
    }

    public int rebuild() {
        List<Reminder> pending = reminders.findByStatus(ReminderStatus.PENDING);
        pending.forEach(r -> queue.add(r.getId(), r.getDueAt()));
        return pending.size();
    }
}
