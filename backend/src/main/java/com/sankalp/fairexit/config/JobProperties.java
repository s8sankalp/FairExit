package com.sankalp.fairexit.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxAttempts  total tries per job (3 = first try plus 2 retries)
 * @param batchSize    jobs taken per poll
 * @param retryBackoff wait before the first retry; each later retry waits 4 times longer
 * @param staleAfter   a RUNNING job older than this is assumed dead and queued again
 */
@ConfigurationProperties(prefix = "fairexit.jobs")
public record JobProperties(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("5") int batchSize,
        @DefaultValue("5s") Duration retryBackoff,
        @DefaultValue("2m") Duration staleAfter) {
}
