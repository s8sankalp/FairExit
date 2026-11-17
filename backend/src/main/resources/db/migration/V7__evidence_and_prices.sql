-- ---------------------------------------------------------------------------
-- Admins (for the price CSV import). Promoted from the fairexit.admin.emails setting.
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';

-- ---------------------------------------------------------------------------
-- Evidence links: the tenant points a bill line at vault photos and says what they show.
-- A link is a claim about evidence, not evidence, so it lives outside the hash chain.
-- ---------------------------------------------------------------------------
CREATE TABLE line_evidence (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id     UUID        NOT NULL REFERENCES deduction_lines (id) ON DELETE CASCADE,
    document_id UUID        NOT NULL REFERENCES documents (id),
    observation VARCHAR(20) NOT NULL,             -- DAMAGE_PRESENT or NO_DAMAGE
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_line_evidence UNIQUE (line_id, document_id)
);

-- ---------------------------------------------------------------------------
-- Repair prices
-- ---------------------------------------------------------------------------
CREATE TABLE price_points (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    category   VARCHAR(20)   NOT NULL,
    city       VARCHAR(60)   NOT NULL,
    flat_size  VARCHAR(20)   NOT NULL,
    amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    source     VARCHAR(10)   NOT NULL,           -- COLLECTED (imported quotes) or USER
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_price_points_key ON price_points (category, city, flat_size);

-- Rebuilt by the nightly job; read through a Redis cache
CREATE TABLE price_stats (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    category     VARCHAR(20)   NOT NULL,
    city         VARCHAR(60)   NOT NULL,
    flat_size    VARCHAR(20)   NOT NULL,
    p50          NUMERIC(12,2) NOT NULL,
    p90          NUMERIC(12,2) NOT NULL,
    n            INT           NOT NULL,          -- points used, after dropping outliers
    refreshed_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ux_price_stats_key UNIQUE (category, city, flat_size)
);

-- ---------------------------------------------------------------------------
-- Rules 4, 5 and 6, and version 2 of rule 3.
-- R3 v2 adds one parameter: a painting or cleaning charge that the tenant's own move out
-- photos show as real damage is not "routine", so wear and tear no longer applies.
-- v1 rows stay, so old audits that name R3@1 can still be explained.
-- ---------------------------------------------------------------------------
INSERT INTO rules (rule_id, version, state, rule_type, priority, parameters, reason_template)
SELECT r.rule_id, r.version, s.state, r.rule_type, r.priority, r.parameters::jsonb, r.reason_template
FROM (VALUES
    ('R3', 2, 'WEAR_AND_TEAR', 30,
     '{"categories": ["PAINTING", "CLEANING"], "minTenancyMonths": 11, "skipWhenDamageShown": true}',
     '"{item}" after {months} months is usually treated as normal wear and tear, and no agreement clause makes the tenant pay for it.'),
    ('R4', 1, 'PRESENT_AT_MOVE_IN', 40,
     '{}',
     '"{item}": your move in photos show this was already there when you moved in.'),
    ('R5', 1, 'PRICE_CHECK', 50,
     '{"categories": ["PAINTING", "CLEANING", "REPAIR"], "minPoints": 10}',
     '"{item}" at {amount} is above the usual range: 9 in 10 of {n} known prices for {category} for a {flatSize} in {city} are {p90} or less.'),
    ('R6', 1, 'DAMAGE_WITH_EVIDENCE', 60,
     '{"categories": ["PAINTING", "CLEANING", "REPAIR", "OTHER"], "minPoints": 10}',
     '"{item}": the damage shows in your move out photos but not in your move in photos, and the price is within the usual range.')
) AS r (rule_id, version, rule_type, priority, parameters, reason_template)
CROSS JOIN (VALUES (NULL::VARCHAR), ('KA'), ('KL')) AS s (state);
