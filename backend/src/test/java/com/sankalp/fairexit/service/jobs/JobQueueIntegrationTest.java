package com.sankalp.fairexit.service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobStatus;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.repository.JobRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The queue against real PostgreSQL: SKIP LOCKED, crash recovery, and running with no API key. */
class JobQueueIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobService jobService;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobWorker worker;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyQueue() {
        jdbc.update("DELETE FROM jobs");
    }

    private Job enqueue() {
        return jobService.enqueue(JobType.EXTRACT_BILL, Map.of(
                "billId", UUID.randomUUID().toString(), "userId", UUID.randomUUID().toString()));
    }

    @Test
    void aClaimedJobIsNotHandedOutTwice() {
        Job job = enqueue();

        List<Job> first = jobService.claimDue();
        List<Job> second = jobService.claimDue();

        assertThat(first).extracting(Job::getId).containsExactly(job.getId());
        assertThat(second).isEmpty();
        Job stored = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(stored.getAttempts()).isEqualTo(1);
    }

    /**
     * Two workers at the same moment. Worker A holds the row lock inside an open
     * transaction. Worker B must get nothing and must not wait: that is SKIP LOCKED.
     */
    @Test
    void aRowLockedByOneWorkerIsSkippedByAnotherWithoutWaiting() throws Exception {
        enqueue();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch workerAHasTheLock = new CountDownLatch(1);
        CountDownLatch letWorkerAFinish = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> workerA = pool.submit(() -> tx.execute(status -> {
                int taken = jobRepository.lockDue(Instant.now(), 10).size();
                workerAHasTheLock.countDown();
                awaitQuietly(letWorkerAFinish);
                return taken;
            }));
            assertThat(workerAHasTheLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> workerB = pool.submit(() ->
                    tx.execute(status -> jobRepository.lockDue(Instant.now(), 10).size()));

            // Without SKIP LOCKED this get() would time out, because B would wait for A's lock
            assertThat(workerB.get(5, TimeUnit.SECONDS)).isZero();
            letWorkerAFinish.countDown();
            assertThat(workerA.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            letWorkerAFinish.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aJobWhoseWorkerDiedIsQueuedAgain() {
        Job job = enqueue();
        jobService.claimDue();
        // Pretend the worker took it 10 minutes ago and then crashed
        jdbc.update("UPDATE jobs SET locked_at = now() - interval '10 minutes' WHERE id = ?", job.getId());

        assertThat(jobService.recoverStale()).isEqualTo(1);

        List<Job> again = jobService.claimDue();
        assertThat(again).extracting(Job::getId).containsExactly(job.getId());
        assertThat(again.get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void aFreshlyRunningJobIsNotTreatedAsDead() {
        enqueue();
        jobService.claimDue();

        assertThat(jobService.recoverStale()).isZero();
    }

    /** No Gemini key in tests: the app must still work, with lines typed by hand. */
    @Test
    void withoutAnApiKeyThePastedBillFailsFastAndManualEntryStillWorks() throws Exception {
        TokenResponse user = newLoggedInUser();
        Map<String, Object> tenancy = new HashMap<>();
        tenancy.put("city", "Kochi");
        tenancy.put("state", "KL");
        tenancy.put("flatSize", "ONE_BHK");
        tenancy.put("rent", 15000);
        tenancy.put("deposit", 50000);
        tenancy.put("startDate", "2024-01-01");
        String tenancyId = JsonPath.read(mvc.perform(post("/api/tenancies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(tenancy)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        String billId = JsonPath.read(mvc.perform(post("/api/tenancies/" + tenancyId + "/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "Painting 20000, cleaning 3000"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EXTRACTING"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(worker.runOnce()).isEqualTo(1);   // one try, no retries: a retry cannot add a key
        assertThat(worker.runOnce()).isZero();

        mvc.perform(get("/api/bills/" + billId).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.status").value("EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.extractionError").value(
                        "We could not read this bill automatically. Please enter the lines by hand."));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/bills/" + billId + "/lines")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lines", List.of(
                                Map.of("item", "Painting", "category", "PAINTING", "amount", 20000))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.extractionError").doesNotExist());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
