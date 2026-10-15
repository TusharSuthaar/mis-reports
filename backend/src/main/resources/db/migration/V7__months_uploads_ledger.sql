-- Phase 4: monthly periods, income-file uploads with their rows, and the brokerage ledger lines.

-- One period per product per calendar month.
CREATE TABLE period (
    id           BIGSERIAL PRIMARY KEY,
    product_type VARCHAR(10) NOT NULL DEFAULT 'MF',
    month        DATE        NOT NULL,   -- first day of the month
    from_date    DATE        NOT NULL,
    to_date      DATE        NOT NULL,
    status       VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PROCESSED', 'LOCKED')),
    locked_by    VARCHAR(50),
    locked_at    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_type, month),
    CHECK (from_date = month AND to_date >= from_date)
);

-- An uploaded income file. STAGED = read and checked, not yet saved to the ledger.
CREATE TABLE upload (
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(20)  NOT NULL,
    period_id    BIGINT       NOT NULL REFERENCES period (id),
    file_name    VARCHAR(300) NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    sha256       VARCHAR(64)  NOT NULL,
    status       VARCHAR(12)  NOT NULL CHECK (status IN ('STAGED', 'COMMITTED', 'SUPERSEDED', 'DISCARDED')),
    totals_json  JSONB        NOT NULL,   -- the file's Total row
    warnings     INT          NOT NULL DEFAULT 0,
    uploaded_by  VARCHAR(50)  NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    committed_by VARCHAR(50),
    committed_at TIMESTAMPTZ
);
-- The same file is never ingested twice (a discarded upload may be tried again).
CREATE UNIQUE INDEX ux_upload_sha ON upload (sha256) WHERE status <> 'DISCARDED';
CREATE INDEX ix_upload_period ON upload (period_id);

-- Each client row of an upload, as read. PAN only encrypted.
CREATE TABLE staged_row (
    id              BIGSERIAL PRIMARY KEY,
    upload_id       BIGINT        NOT NULL REFERENCES upload (id),
    row_no          INT           NOT NULL,
    client_code     VARCHAR(50)   NOT NULL,
    client_code2    VARCHAR(50),
    client_name     VARCHAR(300)  NOT NULL,
    raw_rm          VARCHAR(200),
    raw_partner     VARCHAR(200),
    gross           NUMERIC(18, 4) NOT NULL,
    gst             NUMERIC(18, 4) NOT NULL,
    net             NUMERIC(18, 4) NOT NULL,
    client_id       BIGINT        NOT NULL REFERENCES client (id),
    new_client      BOOLEAN       NOT NULL DEFAULT FALSE,
    rm_from_file    BOOLEAN       NOT NULL DEFAULT FALSE   -- new client given the RM the file names
);
CREATE INDEX ix_staged_row_upload ON staged_row (upload_id);

-- Ledger: one line per client per product per period. Append-only: a line can only be voided, never changed or removed.
CREATE TABLE brokerage_line (
    id            BIGSERIAL PRIMARY KEY,
    period_id     BIGINT        NOT NULL REFERENCES period (id),
    client_id     BIGINT        NOT NULL REFERENCES client (id),
    product_type  VARCHAR(10)   NOT NULL,
    gross         NUMERIC(18, 4) NOT NULL,
    gst           NUMERIC(18, 4) NOT NULL,
    net           NUMERIC(18, 4) NOT NULL,
    upload_id     BIGINT        NOT NULL REFERENCES upload (id),
    staged_row_id BIGINT        NOT NULL REFERENCES staged_row (id),
    voided        BOOLEAN       NOT NULL DEFAULT FALSE,
    voided_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (gross - gst = net OR abs(gross - gst - net) <= 0.01)
);
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type) WHERE NOT voided;
CREATE INDEX ix_brokerage_line_upload ON brokerage_line (upload_id);

CREATE FUNCTION brokerage_line_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'brokerage_line is append-only';
    END IF;
    IF NEW.voided = TRUE AND OLD.voided = FALSE
       AND (NEW.period_id, NEW.client_id, NEW.product_type, NEW.gross, NEW.gst, NEW.net, NEW.upload_id, NEW.staged_row_id)
           IS NOT DISTINCT FROM (OLD.period_id, OLD.client_id, OLD.product_type, OLD.gross, OLD.gst, OLD.net, OLD.upload_id, OLD.staged_row_id) THEN
        RETURN NEW;  -- voiding is the only allowed change
    END IF;
    RAISE EXCEPTION 'brokerage_line is append-only (only voiding is allowed)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_brokerage_line_append_only
    BEFORE UPDATE OR DELETE ON brokerage_line
    FOR EACH ROW EXECUTE FUNCTION brokerage_line_append_only();
