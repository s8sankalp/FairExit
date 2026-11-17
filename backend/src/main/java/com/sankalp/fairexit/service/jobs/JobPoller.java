package com.sankalp.fairexit.service.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Calls the worker on a timer. Kept apart from JobWorker so tests can switch the
 * timer off (fairexit.jobs.worker-enabled=false) and call runOnce() themselves.
 */
@Component
@ConditionalOnProperty(name = "fairexit.jobs.worker-enabled", havingValue = "true", matchIfMissing = true)
public class JobPoller {

    private final JobWorker worker;

    public JobPoller(JobWorker worker) {
        this.worker = worker;
    }

    // fixedDelay: the next poll starts only after this one ends, so polls never overlap
    @Scheduled(fixedDelayString = "${fairexit.jobs.poll-interval-ms:1000}")
    public void poll() {
        worker.runOnce();
    }
}
