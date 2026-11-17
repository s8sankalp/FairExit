package com.sankalp.fairexit.service.jobs;

import com.sankalp.fairexit.config.JobProperties;
import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobStatus;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.repository.JobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The job queue, kept in PostgreSQL. Every method is one short transaction.
 * The slow work itself runs outside any transaction (see JobWorker), so no row
 * lock or database connection is held during a 20 second LLM call.
 */
@Service
public class JobService {

    private final JobRepository jobs;
    private final JobProperties properties;
    private final Clock clock;

    public JobService(JobRepository jobs, JobProperties properties, Clock clock) {
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    /** Joins the caller's transaction: the job is saved only if the caller's work is. */
    @Transactional
    public Job enqueue(JobType type, Map<String, String> payload) {
        return jobs.save(new Job(type, payload, clock.instant()));
    }

    /** Takes due jobs and marks them RUNNING. After commit they belong to this worker. */
    @Transactional
    public List<Job> claimDue() {
        Instant now = clock.instant();
        List<Job> due = jobs.lockDue(now, properties.batchSize());
        due.forEach(job -> job.markRunning(now));
        return due;
    }

    @Transactional
    public void complete(UUID jobId) {
        jobs.findById(jobId).ifPresent(job -> job.markDone(clock.instant()));
    }

    /** Returns true when the job was queued for another try, false when it is now FAILED. */
    @Transactional
    public boolean retryOrFail(UUID jobId, String error) {
        Job job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return false;
        }
        Instant now = clock.instant();
        if (job.getAttempts() >= properties.maxAttempts()) {
            job.markFailed(error, now);
            return false;
        }
        job.retryAt(now.plus(backoff(job.getAttempts())), error, now);
        return true;
    }

    @Transactional
    public void fail(UUID jobId, String error) {
        jobs.findById(jobId).ifPresent(job -> job.markFailed(error, clock.instant()));
    }

    /** Crash recovery: a job stuck in RUNNING for too long goes back to PENDING. */
    @Transactional
    public int recoverStale() {
        Instant now = clock.instant();
        return jobs.requeueStale(JobStatus.RUNNING, JobStatus.PENDING,
                now.minus(properties.staleAfter()), now);
    }

    /** 5s after the first failure, 20s after the second, 80s after the third ... */
    Duration backoff(int attempts) {
        return properties.retryBackoff().multipliedBy((long) Math.pow(4, Math.max(0, attempts - 1)));
    }
}
