package com.sankalp.fairexit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One unit of background work. State changes go through the methods below. */
@Entity
@Table(name = "jobs")
public class Job {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private JobType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, String> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
        // for JPA
    }

    public Job(JobType type, Map<String, String> payload, Instant now) {
        this.type = type;
        this.payload = Map.copyOf(payload);
        this.status = JobStatus.PENDING;
        this.runAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A worker takes the job. Each take counts as one attempt. */
    public void markRunning(Instant now) {
        this.status = JobStatus.RUNNING;
        this.attempts++;
        this.lockedAt = now;
        this.updatedAt = now;
    }

    public void markDone(Instant now) {
        this.status = JobStatus.DONE;
        this.lockedAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void retryAt(Instant when, String error, Instant now) {
        this.status = JobStatus.PENDING;
        this.runAt = when;
        this.lockedAt = null;
        this.lastError = shorten(error);
        this.updatedAt = now;
    }

    public void markFailed(String error, Instant now) {
        this.status = JobStatus.FAILED;
        this.lockedAt = null;
        this.lastError = shorten(error);
        this.updatedAt = now;
    }

    private static String shorten(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public JobType getType() {
        return type;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLastError() {
        return lastError;
    }
}
