package com.sankalp.fairexit.service.jobs;

import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobType;

/**
 * Does the work for one job type. A handler must be safe to run twice for the same job
 * (idempotent): after a crash the queue hands the job out again.
 */
public interface JobHandler {

    JobType type();

    /** Throw PermanentJobException to stop at once; any other exception is retried. */
    void handle(Job job) throws Exception;

    /** Called once when the job will not be tried again, so the handler can tell the user. */
    default void onGiveUp(Job job, Exception cause) {
    }
}
