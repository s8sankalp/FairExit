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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    private UUID tenancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ReminderType type;

    @Column(name = "event_date", nullable = false, updatable = false)
    private LocalDate eventDate;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @Column(nullable = false, updatable = false, length = 10)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReminderStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reminder() {
        // for JPA
    }

    public Reminder(UUID tenancyId, ReminderType type, LocalDate eventDate, Instant dueAt) {
        this.tenancyId = tenancyId;
        this.type = type;
        this.eventDate = eventDate;
        this.dueAt = dueAt;
        this.channel = "EMAIL";
        this.status = ReminderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenancyId() {
        return tenancyId;
    }

    public ReminderType getType() {
        return type;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public String getChannel() {
        return channel;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
