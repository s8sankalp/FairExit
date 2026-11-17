package com.sankalp.fairexit.service.reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sankalp.fairexit.AbstractIntegrationTest;
import com.sankalp.fairexit.dto.TokenResponse;
import com.sankalp.fairexit.model.Reminder;
import com.sankalp.fairexit.model.ReminderStatus;
import com.sankalp.fairexit.repository.ReminderRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Module 8 "done when": a reminder is sent once after a forced restart.
 * PostgreSQL and Redis are real; the mail server is a mock that counts emails.
 */
class ReminderIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.now();

    @MockBean
    private JavaMailSender mailSender;
    @Autowired
    private ReminderDispatcher dispatcher;
    @Autowired
    private ReminderIndex index;
    @Autowired
    private ReminderQueue queue;
    @Autowired
    private ReminderRepository reminders;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;

    private String tenancyId;
    private TokenResponse user;

    /** A tenancy ending in 40 days with 30 days notice: reminders in 7, 39 and 70 days. */
    @BeforeEach
    void tenancyWithReminders() throws Exception {
        jdbc.update("DELETE FROM reminders");
        user = newLoggedInUser();
        Map<String, Object> body = new HashMap<>();
        body.put("city", "Bengaluru");
        body.put("state", "KA");
        body.put("flatSize", "TWO_BHK");
        body.put("rent", 30000);
        body.put("deposit", 150000);
        body.put("startDate", "2024-06-01");
        body.put("endDate", LocalDate.now(ReminderPlanner.INDIA).plusDays(40).toString());
        body.put("noticeDays", 30);
        tenancyId = JsonPath.read(mvc.perform(post("/api/tenancies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/tenancies/" + tenancyId + "/reminders").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("NOTICE_PERIOD"))
                .andExpect(jsonPath("$[1].type").value("MOVE_OUT"))
                .andExpect(jsonPath("$[2].type").value("REFUND_DUE"))
                .andExpect(jsonPath("$[2].status").value("PENDING"));
        assertThat(queue.size()).isEqualTo(3);
        reset(mailSender);
    }

    private static Instant inDays(int days) {
        return NOW.plus(Duration.ofDays(days));
    }

    private void forcedRestart() {
        // Redis restarts too: the sorted set and every lock are gone
        try (RedisConnection c = redis.getRequiredConnectionFactory().getConnection()) {
            c.serverCommands().flushAll();
        }
        index.rebuild(); // what ReminderScheduling.run() does when the app starts again
    }

    private List<ReminderStatus> statuses() {
        return reminders.findByTenancyIdOrderByDueAtAsc(UUID.fromString(tenancyId)).stream()
                .map(Reminder::getStatus).toList();
    }

    @Test
    void aReminderIsSentOnceEvenAfterAForcedRestart() {
        assertThat(dispatcher.dispatchDue(inDays(8))).isEqualTo(1); // only the notice reminder is due
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        forcedRestart();
        assertThat(queue.size()).isEqualTo(2); // the sent one is not put back

        assertThat(dispatcher.dispatchDue(inDays(8))).isZero();
        verify(mailSender, times(1)).send(any(MimeMessage.class)); // still just the one email

        assertThat(dispatcher.dispatchDue(inDays(100))).isEqualTo(2);
        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertThat(statuses()).containsOnly(ReminderStatus.SENT);
    }

    @Test
    void aRestartThatWipesRedisBeforeSendingLosesNothing() {
        forcedRestart();

        assertThat(dispatcher.dispatchDue(inDays(100))).isEqualTo(3);
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void aLockLeftByAnInstanceThatDiedIsWaitedForThenTheReminderIsSentOnce() {
        Reminder notice = reminders.findByTenancyIdOrderByDueAtAsc(UUID.fromString(tenancyId)).get(0);
        // The dead instance took the lock and never got to send
        redis.opsForValue().set(ReminderDispatcher.LOCK_PREFIX + notice.getId(), "dead-instance", Duration.ofMinutes(5));

        assertThat(dispatcher.dispatchDue(inDays(8))).isZero();
        verify(mailSender, never()).send(any(MimeMessage.class));

        redis.delete(ReminderDispatcher.LOCK_PREFIX + notice.getId()); // the lock's TTL runs out
        assertThat(dispatcher.dispatchDue(inDays(8))).isEqualTo(1);
        assertThat(dispatcher.dispatchDue(inDays(8))).isZero();
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void twoInstancesPollingAtTheSameTimeSendEachReminderOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> poll = () -> dispatcher.dispatchDue(inDays(100));
            List<Future<Integer>> results = pool.invokeAll(List.of(poll, poll));

            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertThat(statuses()).containsOnly(ReminderStatus.SENT);
    }

    /**
     * The one gap that no mail system can close: the mail server may have taken the email
     * when the app saw an error. The retry then carries the same fixed Message-ID, so the
     * tenant's inbox treats both as one email.
     */
    @Test
    void aRetryAfterAFailedSendReusesTheSameMessageId() throws Exception {
        doThrow(new MailSendException("connection reset")).doNothing().when(mailSender).send(any(MimeMessage.class));

        assertThat(dispatcher.dispatchDue(inDays(8))).isZero();   // failed, still PENDING, still queued
        assertThat(dispatcher.dispatchDue(inDays(8))).isEqualTo(1);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(sent.capture());
        sent.getAllValues().get(0).saveChanges();
        sent.getAllValues().get(1).saveChanges();
        assertThat(sent.getAllValues().get(1).getMessageID())
                .isEqualTo(sent.getAllValues().get(0).getMessageID())
                .startsWith("<reminder-");
        Reminder notice = reminders.findByTenancyIdOrderByDueAtAsc(UUID.fromString(tenancyId)).get(0);
        assertThat(notice.getAttempts()).isEqualTo(2);
    }

    @Test
    void recordingTheOutcomeCancelsTheRemindersStillWaiting() throws Exception {
        mvc.perform(post("/api/tenancies/" + tenancyId + "/outcome")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amountReturned", 145300, "daysTaken", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindersCancelled").value(3))
                .andExpect(jsonPath("$.percentReturned").value(96.9));

        assertThat(dispatcher.dispatchDue(inDays(100))).isZero();
        verify(mailSender, never()).send(any(MimeMessage.class));
        mvc.perform(get("/api/tenancies/" + tenancyId + "/reminders").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void replanningNeverResendsAReminderThatWasAlreadySent() throws Exception {
        assertThat(dispatcher.dispatchDue(inDays(8))).isEqualTo(1); // notice reminder sent

        mvc.perform(post("/api/tenancies/" + tenancyId + "/reminders").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("NOTICE_PERIOD"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));

        assertThat(dispatcher.dispatchDue(inDays(100))).isEqualTo(2);
        verify(mailSender, times(3)).send(any(MimeMessage.class)); // 1 + 2, the notice never twice
    }

    @Test
    void aTenancyWithoutAnEndDateCannotHaveReminders() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("city", "Kochi");
        body.put("state", "KL");
        body.put("flatSize", "ONE_BHK");
        body.put("rent", 15000);
        body.put("deposit", 50000);
        body.put("startDate", "2025-01-01");
        String open = JsonPath.read(mvc.perform(post("/api/tenancies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/tenancies/" + open + "/reminders").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isUnprocessableEntity());
    }
}
