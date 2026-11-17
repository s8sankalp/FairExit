package com.sankalp.fairexit.service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

    @Mock
    private JobService jobs;

    private final Job job = new Job(JobType.EXTRACT_BILL, Map.of(), Instant.parse("2026-11-17T10:00:00Z"));
    private final List<Exception> gaveUpWith = new ArrayList<>();

    /** A handler that throws whatever the test sets, and records onGiveUp calls. */
    private Exception toThrow;
    private final JobHandler handler = new JobHandler() {
        @Override
        public JobType type() {
            return JobType.EXTRACT_BILL;
        }

        @Override
        public void handle(Job j) throws Exception {
            if (toThrow != null) {
                throw toThrow;
            }
        }

        @Override
        public void onGiveUp(Job j, Exception cause) {
            gaveUpWith.add(cause);
        }
    };

    @BeforeEach
    void claimOneJob() {
        when(jobs.claimDue()).thenReturn(List.of(job));
    }

    private JobWorker worker() {
        return new JobWorker(jobs, List.of(handler));
    }

    @Test
    void successCompletesTheJob() {
        assertThat(worker().runOnce()).isEqualTo(1);

        verify(jobs).complete(job.getId());
        assertThat(gaveUpWith).isEmpty();
    }

    @Test
    void aTemporaryErrorIsRetriedWithoutTellingTheUser() {
        toThrow = new IllegalStateException("timeout");
        when(jobs.retryOrFail(job.getId(), "timeout")).thenReturn(true);

        worker().runOnce();

        verify(jobs, never()).complete(any());
        assertThat(gaveUpWith).isEmpty();
    }

    @Test
    void whenRetriesRunOutTheHandlerIsTold() {
        toThrow = new IllegalStateException("timeout");
        when(jobs.retryOrFail(job.getId(), "timeout")).thenReturn(false);

        worker().runOnce();

        assertThat(gaveUpWith).containsExactly(toThrow);
    }

    @Test
    void aPermanentErrorFailsAtOnceWithNoRetry() {
        toThrow = new PermanentJobException("daily limit");

        worker().runOnce();

        verify(jobs).fail(job.getId(), "daily limit");
        verify(jobs, never()).retryOrFail(any(), anyString());
        assertThat(gaveUpWith).containsExactly(toThrow);
    }

    @Test
    void aJobTypeWithoutAHandlerFails() {
        new JobWorker(jobs, List.of()).runOnce();

        verify(jobs).fail(eq(job.getId()), anyString());
    }
}
