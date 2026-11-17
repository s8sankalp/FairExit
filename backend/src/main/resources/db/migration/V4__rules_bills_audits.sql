-- ---------------------------------------------------------------------------
-- Rules: stored as data, versioned, scoped per state. Logic per rule_type is Java.
-- state NULL = general guidance used for every state without its own rows.
-- ---------------------------------------------------------------------------
CREATE TABLE rules (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         VARCHAR(10) NOT NULL,          -- R1, R2 ...
    version         INT         NOT NULL,
    state           VARCHAR(2),
    rule_type       VARCHAR(40) NOT NULL,          -- which Java class runs it
    priority        INT         NOT NULL,          -- evaluation order
    parameters      JSONB       NOT NULL DEFAULT '{}',
    reason_template TEXT        NOT NULL,
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_rules_id_state_version ON rules (rule_id, COALESCE(state, '*'), version);

-- Same version 1 rules for Karnataka, Kerala and the general set.
-- They can diverge later by adding a version 2 row for one state only.
INSERT INTO rules (rule_id, version, state, rule_type, priority, parameters, reason_template)
SELECT r.rule_id, 1, s.state, r.rule_type, r.priority, r.parameters::jsonb, r.reason_template
FROM (VALUES
    ('R1', 'DUES_MATCH', 10,
     '{"tolerance": 1, "maxRentMonths": 3}',
     'Matches {match}.'),
    ('R2', 'AGREEMENT_CHECK', 20,
     '{"requiresClause": ["SOCIETY_CHARGE", "OTHER"]}',
     'Your agreement has no clause that lets the landlord charge for "{item}" ({category}).'),
    ('R3', 'WEAR_AND_TEAR', 30,
     '{"categories": ["PAINTING", "CLEANING"], "minTenancyMonths": 11}',
     '"{item}" after {months} months is usually treated as normal wear and tear, and no agreement clause makes the tenant pay for it.'),
    ('R7', 'NEEDS_INFO', 70,
     '{"questions": {
         "PAINTING": "Ask for photos of the wall damage and the painter''s invoice.",
         "CLEANING": "Ask for the cleaning invoice and what was cleaned beyond normal cleaning.",
         "REPAIR": "Ask for photos of the damage and the repair invoice.",
         "UTILITY": "Ask for a copy of the bill this amount comes from.",
         "RENT": "Ask which months of rent this covers.",
         "SOCIETY_CHARGE": "Ask which agreement clause or society rule allows this charge.",
         "OTHER": "Ask what this charge is for and which agreement clause allows it."}}',
     'Not enough information to decide. Ask the landlord: {question}')
) AS r (rule_id, rule_type, priority, parameters, reason_template)
CROSS JOIN (VALUES (NULL::VARCHAR), ('KA'), ('KL')) AS s (state);

-- ---------------------------------------------------------------------------
-- Deduction bills and their lines
-- ---------------------------------------------------------------------------
CREATE TABLE deduction_bills (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenancy_id    UUID        NOT NULL REFERENCES tenancies (id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL,            -- EXTRACTING, CONFIRMED, AUDITED
    source        VARCHAR(20) NOT NULL,            -- MANUAL now; TEXT and FILE in later modules
    raw_text_hash VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_deduction_bills_tenancy ON deduction_bills (tenancy_id, created_at DESC);

CREATE TABLE deduction_lines (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id        UUID          NOT NULL REFERENCES deduction_bills (id) ON DELETE CASCADE,
    line_no        INT           NOT NULL,
    item           VARCHAR(200)  NOT NULL,
    category       VARCHAR(20)   NOT NULL,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    room           VARCHAR(60),
    edited_by_user BOOLEAN       NOT NULL DEFAULT false,
    CONSTRAINT ux_deduction_lines_bill_line_no UNIQUE (bill_id, line_no)
);

-- ---------------------------------------------------------------------------
-- Audits: immutable results. Running again creates a new audit row.
-- ---------------------------------------------------------------------------
CREATE TABLE audits (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id            UUID          NOT NULL REFERENCES deduction_bills (id) ON DELETE CASCADE,
    rules_version      VARCHAR(200)  NOT NULL,
    rules_coverage     VARCHAR(20)   NOT NULL,
    tenancy_months     INT           NOT NULL,
    total_claimed      NUMERIC(12,2) NOT NULL,
    total_fair         NUMERIC(12,2) NOT NULL,
    total_questionable NUMERIC(12,2) NOT NULL,
    total_needs_info   NUMERIC(12,2) NOT NULL,
    suggested_refund   NUMERIC(12,2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_audits_bill ON audits (bill_id, created_at DESC);

CREATE TABLE audit_verdicts (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id     UUID        NOT NULL REFERENCES audits (id) ON DELETE CASCADE,
    line_id      UUID        NOT NULL REFERENCES deduction_lines (id) ON DELETE CASCADE,
    verdict      VARCHAR(20) NOT NULL,
    rule_ids     JSONB       NOT NULL,
    reason       TEXT        NOT NULL,
    evidence_ids JSONB       NOT NULL DEFAULT '[]'
);

CREATE INDEX ix_audit_verdicts_audit ON audit_verdicts (audit_id);
