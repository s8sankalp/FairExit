package com.sankalp.fairexit.service;

import com.sankalp.fairexit.model.Job;
import com.sankalp.fairexit.model.JobType;
import com.sankalp.fairexit.service.jobs.JobHandler;
import com.sankalp.fairexit.service.storage.ObjectStorage;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/** Deletes the files of a deleted account. Deleting a file twice is harmless, so retries are safe. */
@Component
public class DeleteObjectsHandler implements JobHandler {

    private final ObjectStorage storage;

    public DeleteObjectsHandler(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public JobType type() {
        return JobType.DELETE_OBJECTS;
    }

    @Override
    public void handle(Job job) {
        Arrays.stream(job.getPayload().getOrDefault(AccountService.PAYLOAD_KEYS, "").split("\n"))
                .filter(key -> !key.isBlank())
                .forEach(storage::delete);
    }
}
