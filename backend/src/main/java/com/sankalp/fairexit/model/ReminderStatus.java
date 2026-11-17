package com.sankalp.fairexit.model;

public enum ReminderStatus {
    PENDING,
    SENT,
    /** Not needed any more, for example the outcome is recorded or the dates changed. */
    CANCELLED
}
