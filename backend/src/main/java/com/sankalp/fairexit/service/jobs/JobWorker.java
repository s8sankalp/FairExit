package com.sankalp.fairexit.service.jobs;

import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs due jobs. Several app instances can run this at the same time:
 * SKIP LOCKED in the queue query makes sure each job goes to one worker only.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobService jobs;
    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);

    public JobWorker(JobService jobs, List<JobHandler> handlerList) {
        this.jobs = jobs;
        handlerList.forEach(handler -> handlers.put(handler.type(), handler));
    }

    /** One poll: recover dead jobs, take a batch, run each. Returns how many jobs ran. */
    public int runOnce() {
        int recovered = jobs.recoverStale();
        if (recovered > 0) {
            log.warn("Requeued {} jobs whose worker stopped", recovered);
        }
        List<Job> claimed = jobs.claimDue();
        claimed.forEach(this::process);
        return claimed.size();
    }

    private void process(Job job) {
        JobHandler handler = handlers.get(job.getType());
        if (handler == null) {
            jobs.fail(job.getId(), "No handler for job type " + job.getType());
            return;
        }
        try {
            handler.handle(job);
            jobs.complete(job.getId());
        } catch (PermanentJobException e) {
            log.info("Job {} ({}) stopped: {}", job.getId(), job.getType(), e.getMessage());
            jobs.fail(job.getId(), e.getMessage());
            giveUp(handler, job, e);
        } catch (Exception e) {
            boolean willRetry = jobs.retryOrFail(job.getId(), String.valueOf(e.getMessage()));
            log.warn("Job {} ({}) attempt {} failed, {}: {}", job.getId(), job.getType(),
                    job.getAttempts(), willRetry ? "will retry" : "giving up", e.getMessage());
            if (!willRetry) {
                giveUp(handler, job, e);
            }
        }
    }

    private static void giveUp(JobHandler handler, Job job, Exception cause) {
        try {
            handler.onGiveUp(job, cause);
        } catch (Exception e) {
            log.error("onGiveUp failed for job {}", job.getId(), e);
        }
    }
}
