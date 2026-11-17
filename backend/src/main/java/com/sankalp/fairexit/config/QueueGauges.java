package com.sankalp.fairexit.config;

import com.sankalp.fairexit.model.JobStatus;
import com.sankalp.fairexit.repository.JobRepository;
import com.sankalp.fairexit.service.reminders.ReminderQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/** Queue sizes, read when Prometheus scrapes (every 15 seconds), never on the request path. */
@Configuration
public class QueueGauges {

    public QueueGauges(MeterRegistry registry, JobRepository jobs, ReminderQueue reminders) {
        Gauge.builder("fairexit.jobs.pending", jobs, j -> safe(() -> j.countByStatus(JobStatus.PENDING)))
                .description("Background jobs waiting to run").register(registry);
        Gauge.builder("fairexit.reminders.queued", reminders, r -> safe(r::size))
                .description("Reminders waiting in the Redis sorted set").register(registry);
    }

    /** A gauge must never throw: a database or Redis hiccup reports "no value" instead. */
    private static double safe(java.util.function.LongSupplier read) {
        try {
            return read.getAsLong();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
