package com.sankalp.fairexit.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Accounts with these emails become admins when they register or log in. */
@ConfigurationProperties(prefix = "fairexit.admin")
public record AdminProperties(List<String> emails) {

    public AdminProperties {
        emails = emails == null ? List.of()
                : emails.stream().map(e -> e.trim().toLowerCase(Locale.ROOT)).filter(e -> !e.isEmpty()).toList();
    }

    public boolean isAdmin(String email) {
        return emails.contains(email);
    }
}
