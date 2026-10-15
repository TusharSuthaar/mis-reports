-- Phase 3: which partner shares a client's brokerage, at what % of NET, over time.
-- One partner per client per product at any date (business rule: only one partner is paid).

CREATE TABLE client_partner_share (
    id           BIGSERIAL PRIMARY KEY,
    client_id    BIGINT       NOT NULL REFERENCES client (id),
    partner_id   BIGINT       NOT NULL REFERENCES partner (id),
    product_type VARCHAR(10)  NOT NULL CHECK (product_type IN ('MF', 'AIF', 'INS', 'BOND', 'PREIPO', 'FD', 'LOAN', 'EQUITY', 'PMS')),
    share        NUMERIC(9, 6) NOT NULL CHECK (share >= 0 AND share <= 1),  -- fraction of net: 0.8 = 80%
    valid_from   DATE         NOT NULL,
    valid_to     DATE,
    reason       VARCHAR(500),
    changed_by   VARCHAR(50)  NOT NULL,
    changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (client_id WITH =, product_type WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_cps_partner ON client_partner_share (partner_id);
