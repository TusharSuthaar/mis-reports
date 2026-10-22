-- Equity brokerage (R-89): the broker's monthly "Brokerage Due" report (SMC Global) uploaded on Data uploads. Every trade
-- row is kept; a month's later upload replaces the earlier one. Each client's franchise share (Epsilon's part) for the
-- month becomes one EQUITY line when the month is processed, split with the client's own partner % like MF.
CREATE TABLE equity_upload (
    id           BIGSERIAL PRIMARY KEY,
    month        DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    file_name    VARCHAR(300)   NOT NULL,
    broker       VARCHAR(200),
    trades       INT            NOT NULL,
    clients      INT            NOT NULL,
    gross_total  NUMERIC(16, 2) NOT NULL,
    share_total  NUMERIC(16, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REPLACED')),
    uploaded_by  VARCHAR(50)    NOT NULL,
    uploaded_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_equity_upload_active ON equity_upload (month) WHERE status = 'ACTIVE';

CREATE TABLE equity_trade (
    id             BIGSERIAL PRIMARY KEY,
    upload_id      BIGINT         NOT NULL REFERENCES equity_upload (id),
    row_no         INT            NOT NULL,
    party_code     VARCHAR(40)    NOT NULL,
    pan_encrypted  TEXT           NOT NULL,
    pan_hash       VARCHAR(64)    NOT NULL,
    client_name    VARCHAR(300),
    trade_date     DATE           NOT NULL,
    exchange       VARCHAR(10),
    gross          NUMERIC(16, 4) NOT NULL,
    share          NUMERIC(16, 4) NOT NULL,
    client_id      BIGINT
);
CREATE INDEX ix_equity_trade_upload ON equity_trade (upload_id);
CREATE INDEX ix_equity_trade_pan ON equity_trade (pan_hash);

ALTER TABLE brokerage_line ADD COLUMN equity_upload_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL
        OR loan_entry_id IS NOT NULL OR gift_entry_id IS NOT NULL OR equity_upload_id IS NOT NULL);
