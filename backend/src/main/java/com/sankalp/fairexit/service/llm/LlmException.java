package com.sankalp.fairexit.service.llm;

public class LlmException extends RuntimeException {

    public enum Kind {
        /** No API key. */
        NOT_CONFIGURED(false),
        /** The user's daily limit is used up. */
        QUOTA_EXCEEDED(false),
        /** The provider refused the request (bad key, bad request). */
        REJECTED(false),
        /** A valid answer with no deduction lines in it. */
        NO_LINES(false),
        /** Timeout, rate limit or server error at the provider. */
        UNAVAILABLE(true),
        /** The answer was not the JSON we asked for. */
        BAD_OUTPUT(true);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean retryable() {
        return kind.retryable;
    }
}
