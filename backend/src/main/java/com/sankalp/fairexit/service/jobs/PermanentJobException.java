package com.sankalp.fairexit.service.jobs;

/** A failure that a retry cannot fix. The message is safe to show to the user. */
public class PermanentJobException extends RuntimeException {

    public PermanentJobException(String message) {
        super(message);
    }
}
