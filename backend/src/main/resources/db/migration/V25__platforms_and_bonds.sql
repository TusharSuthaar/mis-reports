-- Platforms an investment is booked through (GoldenPi, BondsIndia, SMC…) and their dated brokerage terms (R-75);
-- bond transactions entered on the Bonds screen (R-76). Bond lines feed the ledger like FD lines (R-74).
CREATE TABLE intermediary (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_intermediary_name ON intermediary (lower(name));

CREATE TABLE intermediary_terms (
    id              BIGSERIAL PRIMARY KEY,
    intermediary_id BIGINT        NOT NULL REFERENCES intermediary (id),
    gst_inclusive   BOOLEAN       NOT NULL,
    tds_percent     NUMERIC(6, 3) NOT NULL CHECK (tds_percent >= 0 AND tds_percent <= 100),
    our_share       NUMERIC(6, 3) NOT NULL CHECK (our_share > 0 AND our_share <= 100),
    valid_from      DATE          NOT NULL,
    valid_to        DATE,
    reason          VARCHAR(500),
    changed_by      VARCHAR(50)   NOT NULL,
    changed_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (intermediary_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);

CREATE TABLE bond_entry (
    id                  BIGSERIAL PRIMARY KEY,
    month               DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id               BIGINT         NOT NULL,
    partner_id          BIGINT,
    client_id           BIGINT         NOT NULL,
    mobile              VARCHAR(20),
    email               VARCHAR(200),
    address             VARCHAR(500),
    intermediary_id     BIGINT         NOT NULL REFERENCES intermediary (id),
    bond_name           VARCHAR(300)   NOT NULL,
    isin                VARCHAR(40),
    coupon_percent      NUMERIC(7, 4),
    ytm_percent         NUMERIC(7, 4),
    payout_frequency    VARCHAR(20),
    payment_schedule    VARCHAR(300),
    investment_date     DATE           NOT NULL,
    allotment_date      DATE,
    maturity_date       DATE,
    quantity            NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
    rate_per_unit       NUMERIC(18, 4),
    face_value          NUMERIC(18, 4),
    investment_amount   NUMERIC(18, 2) NOT NULL CHECK (investment_amount > 0),
    accrued_interest    NUMERIC(18, 2) NOT NULL DEFAULT 0,
    consideration       NUMERIC(18, 2) NOT NULL,
    markup_percent      NUMERIC(7, 4)  NOT NULL CHECK (markup_percent >= 0 AND markup_percent <= 100),
    our_share           NUMERIC(6, 3)  NOT NULL,
    gst_inclusive       BOOLEAN        NOT NULL,
    brokerage           NUMERIC(18, 2) NOT NULL,
    gross               NUMERIC(18, 2) NOT NULL,
    tds_applied         BOOLEAN        NOT NULL,
    tds_percent         NUMERIC(6, 3)  NOT NULL,
    tds                 NUMERIC(18, 2) NOT NULL,
    gst_percent         NUMERIC(6, 3)  NOT NULL,
    gst                 NUMERIC(18, 2) NOT NULL,
    net                 NUMERIC(18, 2) NOT NULL,
    invoice             NUMERIC(18, 2) NOT NULL,
    invoice_raised      BOOLEAN        NOT NULL DEFAULT false,
    invoice_date        DATE,
    payment_received_on DATE,
    payment_status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    on_portal           BOOLEAN        NOT NULL DEFAULT false,
    remarks             VARCHAR(1000),
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50)    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50)    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_bond_entry_month ON bond_entry (month);

-- Bonds pay GST at 18% (R-71). Platform terms say whether a platform's brokerage already includes it.
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('BOND', 18, 0, false, '2022-01-01', 'Starting rate', 'system');

-- Ledger: a line may come from a bond entry too.
ALTER TABLE brokerage_line ADD COLUMN bond_entry_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0)) WHERE NOT voided;

CREATE OR REPLACE VIEW v_line_split AS
SELECT l.id                                                                   AS line_id,
       l.period_id,
       l.product_type,
       l.client_id,
       c.name                                                                 AS client_name,
       c.pan_encrypted,
       l.gross,
       l.gst,
       l.net,
       l.adjusts_period_id,
       max(s.region_at_period)                                                AS region,
       max(s.rd_at_period)                                                    AS rd,
       max(s.branch_at_period)                                                AS branch,
       max(s.rm_at_period)                                                    AS rm,
       max(s.partner_name) FILTER (WHERE s.partner_role = 'SUB_BROKER')       AS sub_broker,
       max(s.share_pct)    FILTER (WHERE s.partner_role = 'SUB_BROKER')       AS sub_broker_pct,
       coalesce(sum(s.amount) FILTER (WHERE s.partner_role = 'SUB_BROKER'), 0) AS sub_broker_amount,
       max(s.partner_name) FILTER (WHERE s.partner_role = 'REFERRAL')         AS referral,
       max(s.share_pct)    FILTER (WHERE s.partner_role = 'REFERRAL')         AS referral_pct,
       coalesce(sum(s.amount) FILTER (WHERE s.partner_role = 'REFERRAL'), 0)  AS referral_amount,
       max(s.partner_name) FILTER (WHERE s.partner_role = 'POSP')             AS posp,
       coalesce(sum(s.amount) FILTER (WHERE s.partner_role = 'POSP'), 0)      AS posp_amount,
       coalesce(sum(s.amount) FILTER (WHERE s.payee_type = 'COMPANY'), 0)     AS epsilon_amount,
       l.fd_entry_id,
       l.bond_entry_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
