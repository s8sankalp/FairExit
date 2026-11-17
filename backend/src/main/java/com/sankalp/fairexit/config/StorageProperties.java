package com.sankalp.fairexit.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param endpoint       where the app itself reaches the store (http://minio:9000 inside Docker)
 * @param publicEndpoint where the browser reaches it, used in presigned URLs (http://localhost:9000)
 * @param urlTtl         how long a presigned upload or download URL works (PRD: 10 minutes)
 * @param maxUploadBytes PRD decision 27: 10 MB per file
 */
@ConfigurationProperties(prefix = "fairexit.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("http://localhost:9000") String publicEndpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("fairexit-vault") String bucket,
        @DefaultValue("10m") Duration urlTtl,
        @DefaultValue("10485760") long maxUploadBytes) {
}
