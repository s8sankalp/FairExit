package com.sankalp.fairexit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fairexit.mail")
public record MailProperties(@DefaultValue("FairExit <reminders@fairexit.local>") String from) {
}
