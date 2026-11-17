package com.sankalp.fairexit.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the bucket on startup. MinIO can start a few seconds after the app in
 * docker-compose, so it retries; if storage never answers the app still starts,
 * because audits and bills work without files.
 */
@Component
public class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);
    private static final int ATTEMPTS = 10;

    private final ObjectStorage storage;

    public BucketInitializer(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                storage.ensureBucket();
                return;
            } catch (RuntimeException e) {
                log.warn("Storage not ready (attempt {}/{}): {}", attempt, ATTEMPTS, e.getMessage());
                Thread.sleep(2000);
            }
        }
        log.error("Storage is not reachable; uploads will fail until it is");
    }
}
