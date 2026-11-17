-- ---------------------------------------------------------------------------
-- Background jobs. A row is the queue entry: it is inserted in the same
-- transaction as the work it belongs to, so a job can never be lost or orphaned.
-- ---------------------------------------------------------------------------
CREATE TABLE jobs (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    type       VARCHAR(30) NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}',
    status     VARCHAR(10) NOT NULL,              -- PENDING, RUNNING, DONE, FAILED
    attempts   INT         NOT NULL DEFAULT 0,
    run_at     TIMESTAMPTZ NOT NULL,              -- not before this time (used for retry backoff)
    locked_at  TIMESTAMPTZ,                       -- when a worker took it
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The worker only ever looks for due PENDING jobs, so index just those rows
CREATE INDEX ix_jobs_due ON jobs (run_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- Bills read by the AI
-- ---------------------------------------------------------------------------
ALTER TABLE deduction_bills
    ADD COLUMN masked_text      TEXT,             -- pasted message AFTER masking phones, emails, PINs, names
    ADD COLUMN stated_total     NUMERIC(12,2),    -- total written in the message, if any
    ADD COLUMN extraction_error VARCHAR(300);
