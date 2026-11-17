package com.sankalp.fairexit.service.reminders;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The timers. On every start (and every 10 minutes) the Redis set is rebuilt from the
 * PENDING rows, so a Redis restart or flush never loses a reminder.
 */
@Component
@ConditionalOnProperty(name = "fairexit.reminders.worker-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduling implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduling.class);

    private final ReminderIndex index;
    private final ReminderDispatcher dispatcher;
    private final Clock clock;

    public ReminderScheduling(ReminderIndex index, ReminderDispatcher dispatcher, Clock clock) {
        this.index = index;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Reminder index rebuilt: {} pending", index.rebuild());
        } catch (RuntimeException e) {
            log.warn("Could not rebuild the reminder index at start: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${fairexit.reminders.poll-interval-ms:30000}")
    public void poll() {
        dispatcher.dispatchDue(clock.instant());
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
    public void reconcile() {
        index.rebuild();
    }
}
