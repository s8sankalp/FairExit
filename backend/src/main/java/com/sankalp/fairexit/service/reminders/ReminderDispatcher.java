package com.sankalp.fairexit.service.reminders;

import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderStatus;
import com.sankalp.fairexit.model.Tenancy;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.ReminderRepository;
import com.sankalp.fairexit.repository.TenancyRepository;
import com.sankalp.fairexit.repository.UserRepository;
import com.sankalp.fairexit.service.RedisLock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends reminders that are due. Safe with many app instances and across restarts:
 *
 *  1. ZRANGEBYSCORE finds due ids in Redis.
 *  2. SET NX lock per reminder: only one instance works on a reminder at a time.
 *  3. Re-read the row: if it is no longer PENDING (sent, cancelled), drop it from the set.
 *  4. Send the email (no database transaction is open during the SMTP call).
 *  5. UPDATE ... SET status = SENT WHERE status = PENDING. Only one caller can ever win.
 *  6. ZREM, then unlock.
 *
 * A crash between 4 and 5 is the one gap: the reminder is still PENDING and is sent again
 * after the restart, with the same fixed Message-ID, so the inbox still shows one email.
 * A crash anywhere else loses nothing and sends nothing twice.
 */
@Component
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);
    static final String LOCK_PREFIX = "lock:reminder:";
    /** Longer than any SMTP call; if a holder dies, the lock frees itself after this. */
    static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final int BATCH = 50;

    private final ReminderQueue queue;
    private final RedisLock lock;
    private final ReminderRepository reminders;
    private final TenancyRepository tenancies;
    private final UserRepository users;
    private final ReminderMailer mailer;

    public ReminderDispatcher(ReminderQueue queue, RedisLock lock, ReminderRepository reminders,
                              TenancyRepository tenancies, UserRepository users, ReminderMailer mailer) {
        this.queue = queue;
        this.lock = lock;
        this.reminders = reminders;
        this.tenancies = tenancies;
        this.users = users;
        this.mailer = mailer;
    }

    /** Returns how many reminders this call sent. */
    public int dispatchDue(Instant now) {
        int sent = 0;
        for (UUID id : queue.due(now, BATCH)) {
            Optional<String> token = lock.tryLock(LOCK_PREFIX + id, LOCK_TTL);
            if (token.isEmpty()) {
                continue; // another instance is on it
            }
            try {
                if (sendOne(id, now)) {
                    sent++;
                }
            } finally {
                lock.unlock(LOCK_PREFIX + id, token.get());
            }
        }
        return sent;
    }

    private boolean sendOne(UUID id, Instant now) {
        Optional<Reminder> found = reminders.findById(id);
        if (found.isEmpty() || found.get().getStatus() != ReminderStatus.PENDING) {
            queue.remove(id); // sent, cancelled or deleted: nothing to do
            return false;
        }
        Reminder reminder = found.get();
        Optional<Tenancy> tenancy = tenancies.findById(reminder.getTenancyId());
        Optional<User> user = tenancy.flatMap(t -> users.findById(t.getUserId()));
        if (user.isEmpty()) {
            queue.remove(id);
            return false;
        }
        try {
            mailer.send(reminder, user.get().getEmail(), user.get().getName(), tenancy.get().getCity());
        } catch (Exception e) {
            // Left in the set: the next poll tries again
            log.warn("Reminder {} not sent: {}", id, e.getMessage());
            reminders.recordFailure(id, shorten(e.getMessage()));
            return false;
        }
        boolean won = reminders.markSent(id, now) == 1;
        queue.remove(id);
        return won;
    }

    private static String shorten(String message) {
        String m = String.valueOf(message);
        return m.length() <= 300 ? m : m.substring(0, 300);
    }
}
