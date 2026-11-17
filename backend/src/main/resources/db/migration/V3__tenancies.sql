CREATE TABLE tenancies (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    city        VARCHAR(60)   NOT NULL,
    state       VARCHAR(2)    NOT NULL,            -- Indian state code, for example KA
    flat_size   VARCHAR(20)   NOT NULL,            -- ONE_RK, ONE_BHK, ...
    rent        NUMERIC(12,2) NOT NULL CHECK (rent > 0),
    deposit     NUMERIC(12,2) NOT NULL CHECK (deposit > 0),
    start_date  DATE          NOT NULL,
    end_date    DATE,                              -- null while the tenant still lives there
    notice_days INT           CHECK (notice_days BETWEEN 0 AND 365),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tenancies_dates CHECK (end_date IS NULL OR end_date > start_date)
);

-- Every read is "this user's tenancies, newest first"
CREATE INDEX ix_tenancies_user_start ON tenancies (user_id, start_date DESC);
