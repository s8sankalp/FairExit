-- ---------------------------------------------------------------------------
-- Evidence Vault. Append only: a stored document is never edited or deleted
-- by the app; a correction is a new record.
--
-- Hash chain per tenancy:
--   record_hash = SHA-256(prev_hash, chain_seq, ids, type, tags, file sha256, size, time)
--   prev_hash   = record_hash of the record before (64 zeros for the first)
-- Changing a file, a row, or the order of rows breaks the chain.
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenancy_id   UUID          NOT NULL REFERENCES tenancies (id) ON DELETE CASCADE,
    type         VARCHAR(20)   NOT NULL,      -- AGREEMENT, BILL, PHOTO, RECEIPT, UTILITY_BILL
    phase        VARCHAR(10)   NOT NULL,      -- MOVE_IN, MOVE_OUT, OTHER
    status       VARCHAR(10)   NOT NULL,      -- PENDING, STORED, REJECTED
    file_name    VARCHAR(200)  NOT NULL,
    content_type VARCHAR(50)   NOT NULL,
    size_bytes   BIGINT        NOT NULL,
    room         VARCHAR(60),
    item         VARCHAR(100),
    amount       NUMERIC(12,2),               -- for utility bills: the amount on the bill
    storage_key  VARCHAR(300)  NOT NULL,
    sha256       VARCHAR(64),                 -- set when stored
    chain_seq    INT,
    prev_hash    VARCHAR(64),
    record_hash  VARCHAR(64),
    uploaded_at  TIMESTAMPTZ,
    reject_reason VARCHAR(200),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_documents_tenancy_seq UNIQUE (tenancy_id, chain_seq)
);

CREATE INDEX ix_documents_tenancy_uploaded ON documents (tenancy_id, uploaded_at);

-- Where the chain ends now. Appends lock this row, so two uploads to the same
-- tenancy are chained one after the other, never both after the same record.
ALTER TABLE tenancies
    ADD COLUMN chain_length INT NOT NULL DEFAULT 0,
    ADD COLUMN chain_head   VARCHAR(64);

-- A bill can now come from an uploaded photo or PDF
ALTER TABLE deduction_bills ADD COLUMN document_id UUID REFERENCES documents (id);

-- ---------------------------------------------------------------------------
-- Agreements and their clauses
-- ---------------------------------------------------------------------------
CREATE TABLE agreements (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenancy_id      UUID         NOT NULL REFERENCES tenancies (id) ON DELETE CASCADE,
    document_id     UUID         NOT NULL REFERENCES documents (id),
    status          VARCHAR(20)  NOT NULL,     -- EXTRACTING, EXTRACTED, FAILED
    error           VARCHAR(300),
    dropped_clauses INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_agreements_tenancy ON agreements (tenancy_id, created_at DESC);

CREATE TABLE clauses (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    agreement_id    UUID        NOT NULL REFERENCES agreements (id) ON DELETE CASCADE,
    tenancy_id      UUID        NOT NULL REFERENCES tenancies (id) ON DELETE CASCADE,
    document_id     UUID        NOT NULL REFERENCES documents (id),
    type            VARCHAR(20) NOT NULL,
    page            INT,
    quote           TEXT        NOT NULL,
    value           JSONB       NOT NULL DEFAULT '{}',
    checked_against VARCHAR(20) NOT NULL       -- TEXT_LAYER or TRANSCRIPTION
);

CREATE INDEX ix_clauses_agreement ON clauses (agreement_id);
