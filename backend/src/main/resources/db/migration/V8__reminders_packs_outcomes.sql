-- ---------------------------------------------------------------------------
-- Reminders. PostgreSQL is the truth; a Redis sorted set (score = due time) is only
-- a fast index of what is due, and is rebuilt from this table on every start.
-- status moves PENDING -> SENT (or CANCELLED) once, with a conditional UPDATE.
-- ---------------------------------------------------------------------------
CREATE TABLE reminders (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenancy_id UUID         NOT NULL REFERENCES tenancies (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,        -- NOTICE_PERIOD, MOVE_OUT, REFUND_DUE
    event_date DATE         NOT NULL,        -- the date the reminder is about
    due_at     TIMESTAMPTZ  NOT NULL,        -- when to send
    channel    VARCHAR(10)  NOT NULL,        -- EMAIL
    status     VARCHAR(10)  NOT NULL,
    sent_at    TIMESTAMPTZ,
    attempts   INT          NOT NULL DEFAULT 0,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- PRD index: only unsent reminders are ever searched by time
CREATE INDEX ix_reminders_due ON reminders (due_at) WHERE sent_at IS NULL;
CREATE INDEX ix_reminders_tenancy ON reminders (tenancy_id);

-- ---------------------------------------------------------------------------
-- Evidence packs: one PDF per request, kept in object storage
-- ---------------------------------------------------------------------------
CREATE TABLE evidence_packs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id    UUID        NOT NULL REFERENCES audits (id) ON DELETE CASCADE,
    tenancy_id  UUID        NOT NULL REFERENCES tenancies (id) ON DELETE CASCADE,
    storage_key VARCHAR(300) NOT NULL,
    sha256      VARCHAR(64) NOT NULL,
    chain_head  VARCHAR(64),
    chain_valid BOOLEAN     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL
);

-- ---------------------------------------------------------------------------
-- Outcomes: what the tenant finally got back (feeds the building score later)
-- ---------------------------------------------------------------------------
CREATE TABLE outcomes (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenancy_id          UUID          NOT NULL UNIQUE REFERENCES tenancies (id) ON DELETE CASCADE,
    building_id         UUID,                         -- buildings are a later feature (F13)
    amount_returned     NUMERIC(12,2) NOT NULL CHECK (amount_returned >= 0),
    days_taken          INT           NOT NULL CHECK (days_taken BETWEEN 0 AND 730),
    receipt_document_id UUID          REFERENCES documents (id),
    verified            BOOLEAN       NOT NULL,       -- true when a stored receipt backs it
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
