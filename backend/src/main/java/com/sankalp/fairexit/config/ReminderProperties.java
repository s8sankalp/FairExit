package com.sankalp.fairexit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param sendHour          hour of day, India time, at which reminders are sent
 * @param defaultRefundDays used for the refund due date when the agreement says nothing
 */
@ConfigurationProperties(prefix = "fairexit.reminders")
public record ReminderProperties(
        @DefaultValue("9") int sendHour,
        @DefaultValue("30") int defaultRefundDays) {
}
