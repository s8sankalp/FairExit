package com.sankalp.fairexit.service;

import com.sankalp.fairexit.exception.ApiException;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.model.User;
import com.sankalp.fairexit.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sankalp.fairexit.service.jobs.JobService;

/**
 * Deletes an account and everything in it (PRD privacy requirement).
 *
 * One transaction: collect the storage keys, queue a job that deletes those files,
 * then delete the user row; ON DELETE CASCADE removes tenancies, documents, bills, audits,
 * agreements, reminders, packs and outcomes with it. The file deletion runs as a job so a
 * storage outage cannot leave the account half deleted: the job retries until it succeeds.
 * Refresh tokens are revoked after the commit. Access tokens die within 15 minutes, and
 * every query is scoped to the user, so nothing is reachable with them meanwhile.
 *
 * Not covered: answers in the LLM cache (keyed by content hash, no PII, gone after 30 days).
 */
@Service
public class AccountService {

    static final String PAYLOAD_KEYS = "keys";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final JobService jobs;
    private final JdbcTemplate jdbc;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
                          JobService jobs, JdbcTemplate jdbc) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.jobs = jobs;
        this.jdbc = jdbc;
    }

    @Transactional
    public void delete(UUID userId, String password) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Password is incorrect");
        }

        List<String> keys = jdbc.queryForList("""
                SELECT d.storage_key FROM documents d JOIN tenancies t ON t.id = d.tenancy_id WHERE t.user_id = ?
                UNION ALL
                SELECT p.storage_key FROM evidence_packs p JOIN tenancies t ON t.id = p.tenancy_id WHERE t.user_id = ?
                """, String.class, userId, userId);
        if (!keys.isEmpty()) {
            jobs.enqueue(JobType.DELETE_OBJECTS, Map.of(PAYLOAD_KEYS, String.join("\n", keys)));
        }
        // Background jobs that still name this user would only find nothing; drop them
        jdbc.update("DELETE FROM jobs WHERE status = 'PENDING' AND payload ->> 'userId' = ?", userId.toString());

        users.delete(user);
        users.flush();
        afterCommit(() -> refreshTokens.revokeAll(userId));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
