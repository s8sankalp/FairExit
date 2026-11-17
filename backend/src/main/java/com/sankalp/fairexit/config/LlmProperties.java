package com.sankalp.fairexit.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fairexit.llm")
public record LlmProperties(
        String apiKey,
        @DefaultValue("gemini-3.8-flash") String model,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("20") int dailyLimit,
        @DefaultValue("30d") Duration cacheTtl) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
