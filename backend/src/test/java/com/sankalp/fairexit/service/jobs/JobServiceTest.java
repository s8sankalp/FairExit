package com.sankalp.fairexit.service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sankalp.fairexit.config.JobProperties;
import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobStatus;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.repository.JobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Mock
    private JobRepository repository;

    private JobService service;

    @BeforeEach
    void setUp() {
        JobProperties properties = new JobProperties(3, 5, Duration.ofSeconds(5), Duration.ofMinutes(2));
        service = new JobService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A job that has been taken by a worker the given number of times. */
    private Job jobAfterAttempts(int attempts) {
        Job job = new Job(JobType.EXTRACT_BILL, Map.of("billId", "b"), NOW);
        for (int i = 0; i < attempts; i++) {
            job.markRunning(NOW);
        }
        when(repository.findById(ID)).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    void claimingMarksJobsRunningAndCountsTheAttempt() {
        Job job = new Job(JobType.EXTRACT_BILL, Map.of(), NOW);
        when(repository.lockDue(NOW, 5)).thenReturn(List.of(job));

        assertThat(service.claimDue()).containsExactly(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getLockedAt()).isEqualTo(NOW);
    }

    @Test
    void firstFailureRetriesAfter5Seconds() {
        Job job = jobAfterAttempts(1);

        assertThat(service.retryOrFail(ID, "timeout")).isTrue();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getRunAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(job.getLastError()).isEqualTo("timeout");
        assertThat(job.getLockedAt()).isNull();
    }

    @Test
    void secondFailureRetriesAfter20Seconds() {
        Job job = jobAfterAttempts(2);

        assertThat(service.retryOrFail(ID, "timeout")).isTrue();
        assertThat(job.getRunAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    void thirdFailureGivesUp() {
        Job job = jobAfterAttempts(3);

        assertThat(service.retryOrFail(ID, "timeout")).isFalse();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void failStopsAtOnceWhateverTheAttemptCount() {
        Job job = jobAfterAttempts(1);

        service.fail(ID, "no api key");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getLastError()).isEqualTo("no api key");
    }

    @Test
    void completeMarksDoneAndClearsTheError() {
        Job job = jobAfterAttempts(2);

        service.complete(ID);

        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getLastError()).isNull();
    }

    @Test
    void veryLongErrorsAreCutTo500Characters() {
        Job job = jobAfterAttempts(3);

        service.retryOrFail(ID, "x".repeat(2000));

        assertThat(job.getLastError()).hasSize(500);
    }

    @Test
    void staleJobsAreRequeuedWithTheRightCutoff() {
        when(repository.requeueStale(JobStatus.RUNNING, JobStatus.PENDING, NOW.minusSeconds(120), NOW))
                .thenReturn(2);

        assertThat(service.recoverStale()).isEqualTo(2);
    }
}
