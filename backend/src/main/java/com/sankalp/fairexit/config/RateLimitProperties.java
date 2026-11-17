package com.sankalp.fairexit.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Token bucket: up to {@code capacity} requests, refilled evenly over {@code period}. */
@ConfigurationProperties(prefix = "fairexit.rate-limit")
public record RateLimitProperties(
        @DefaultValue("60") int capacity,
        @DefaultValue("1m") Duration period) {
}
