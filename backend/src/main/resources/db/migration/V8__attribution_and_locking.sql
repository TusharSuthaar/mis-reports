-- Phase 5: splitting each brokerage line between partners and Epsilon, month processing / locking, adjustments.

-- Who processed a month (locking is already recorded in locked_by / locked_at).
ALTER TABLE period ADD COLUMN processed_by VARCHAR(50);
ALTER TABLE period ADD COLUMN processed_at TIMESTAMPTZ;

-- Adjustment lines: a correction to a LOCKED month, posted into a later open month.
ALTER TABLE brokerage_line ADD COLUMN adjusts_period_id BIGINT REFERENCES period (id);
ALTER TABLE brokerage_line ALTER COLUMN staged_row_id DROP NOT NULL;  -- a client missing from the corrected file has no row
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0))
    WHERE NOT voided;

CREATE OR REPLACE FUNCTION brokerage_line_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'brokerage_line is append-only';
    END IF;
    IF NEW.voided = TRUE AND OLD.voided = FALSE
       AND (NEW.period_id, NEW.client_id, NEW.product_type, NEW.gross, NEW.gst, NEW.net, NEW.upload_id, NEW.staged_row_id, NEW.adjusts_period_id)
           IS NOT DISTINCT FROM
           (OLD.period_id, OLD.client_id, OLD.product_type, OLD.gross, OLD.gst, OLD.net, OLD.upload_id, OLD.staged_row_id, OLD.adjusts_period_id) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'brokerage_line is append-only (only voiding is allowed)';
END;
$$ LANGUAGE plpgsql;

-- Where a correction upload for a locked month was posted.
ALTER TABLE upload ADD COLUMN posted_to_period_id BIGINT REFERENCES period (id);

-- One run of the split for a month. Re-running voids the previous run.
CREATE TABLE attribution_run (
    id            BIGSERIAL PRIMARY KEY,
    period_id     BIGINT      NOT NULL REFERENCES period (id),
    product_type  VARCHAR(10) NOT NULL,
    status        VARCHAR(10) NOT NULL CHECK (status IN ('DONE', 'VOIDED')),
    rules_json    JSONB       NOT NULL,   -- the settings and shares the run used
    rules_hash    VARCHAR(64) NOT NULL,
    lines         INT         NOT NULL,
    started_by    VARCHAR(50) NOT NULL,
    started_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_attribution_run_period ON attribution_run (period_id);

-- Who gets what from one brokerage line. Names and hierarchy are stored AS THEY WERE in that month, so later
-- reorganisations never change history. Append-only: splits are only ever voided.
CREATE TABLE brokerage_split (
    id                BIGSERIAL PRIMARY KEY,
    run_id            BIGINT        NOT NULL REFERENCES attribution_run (id),
    period_id         BIGINT        NOT NULL REFERENCES period (id),
    brokerage_line_id BIGINT        NOT NULL REFERENCES brokerage_line (id),
    client_id         BIGINT        NOT NULL REFERENCES client (id),
    payee_type        VARCHAR(10)   NOT NULL CHECK (payee_type IN ('PARTNER', 'COMPANY')),
    partner_role      VARCHAR(20)   CHECK (partner_role IN ('SUB_BROKER', 'REFERRAL', 'POSP')),
    partner_id        BIGINT        REFERENCES partner (id),
    partner_name      VARCHAR(200),
    share_pct         NUMERIC(9, 6),            -- the % applied (fraction), partners only
    base              NUMERIC(18, 4) NOT NULL,  -- the amount the % was applied to
    amount            NUMERIC(18, 4) NOT NULL,  -- partners rounded to 2 decimals; company takes the rest
    rm_id_at_period     BIGINT REFERENCES employee (id),
    rm_at_period        VARCHAR(200),
    branch_at_period    VARCHAR(100),
    region_at_period    VARCHAR(100),
    rd_at_period        VARCHAR(200),
    is_adjustment     BOOLEAN       NOT NULL DEFAULT FALSE,
    voided            BOOLEAN       NOT NULL DEFAULT FALSE,
    voided_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK ((payee_type = 'PARTNER') = (partner_id IS NOT NULL))
);
CREATE INDEX ix_split_period ON brokerage_split (period_id) WHERE NOT voided;
CREATE INDEX ix_split_line ON brokerage_split (brokerage_line_id);
CREATE INDEX ix_split_partner ON brokerage_split (partner_id) WHERE NOT voided;

CREATE FUNCTION brokerage_split_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'brokerage_split is append-only';
    END IF;
    IF NEW.voided = TRUE AND OLD.voided = FALSE AND NEW.amount = OLD.amount AND NEW.brokerage_line_id = OLD.brokerage_line_id
       AND NEW.partner_id IS NOT DISTINCT FROM OLD.partner_id AND NEW.run_id = OLD.run_id THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'brokerage_split is append-only (only voiding is allowed)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_brokerage_split_append_only
    BEFORE UPDATE OR DELETE ON brokerage_split
    FOR EACH ROW EXECUTE FUNCTION brokerage_split_append_only();
