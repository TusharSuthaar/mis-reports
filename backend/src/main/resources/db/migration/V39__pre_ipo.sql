-- Pre-IPO (unlisted shares), R-92..R-94: the scrips we deal in, each vendor's dated price list ("Available IPOs"), and the
-- deals themselves — a BUY (client buys: our mark-up is added to the vendor's base price) or a BUYBACK (client sells back:
-- our mark-up is taken off the price the vendor pays). Vendors are platforms (intermediary). Deals feed the ledger.
CREATE TABLE preipo_scrip (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(300)   NOT NULL,
    isin       VARCHAR(20),
    face_value NUMERIC(18, 4),
    lot_size   INTEGER CHECK (lot_size IS NULL OR lot_size > 0),
    active     BOOLEAN        NOT NULL DEFAULT true,
    remarks    VARCHAR(500),
    created_by VARCHAR(50)    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by VARCHAR(50)    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_preipo_scrip_name ON preipo_scrip (lower(name));
CREATE UNIQUE INDEX ux_preipo_scrip_isin ON preipo_scrip (upper(isin)) WHERE isin IS NOT NULL;

-- A vendor's base price for a scrip on a day; the client price is base × (1 + mark-up %). A new day's price never
-- overwrites an older one, so the price on any date can be looked up.
CREATE TABLE preipo_price (
    id              BIGSERIAL PRIMARY KEY,
    scrip_id        BIGINT         NOT NULL REFERENCES preipo_scrip (id),
    intermediary_id BIGINT         NOT NULL REFERENCES intermediary (id),
    as_of           DATE           NOT NULL,
    base_price      NUMERIC(18, 4) NOT NULL CHECK (base_price > 0),
    markup_percent  NUMERIC(7, 4)  NOT NULL CHECK (markup_percent >= 0 AND markup_percent <= 100),
    client_price    NUMERIC(18, 4) NOT NULL,
    changed_by      VARCHAR(50)    NOT NULL,
    changed_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (scrip_id, intermediary_id, as_of)
);
CREATE INDEX ix_preipo_price_scrip ON preipo_price (scrip_id, as_of DESC);

CREATE TABLE preipo_entry (
    id               BIGSERIAL PRIMARY KEY,
    kind             VARCHAR(10)    NOT NULL CHECK (kind IN ('BUY', 'BUYBACK')),
    month            DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id            BIGINT         NOT NULL,
    partner_id       BIGINT,
    sharing_percent  NUMERIC(6, 3) CHECK (sharing_percent IS NULL OR (sharing_percent >= 0 AND sharing_percent <= 100)),
    client_id        BIGINT         NOT NULL,
    mobile           VARCHAR(20),
    email            VARCHAR(200),
    dp_name          VARCHAR(200),
    dp_id            VARCHAR(40),
    demat_client_id  VARCHAR(40),
    scrip_id         BIGINT         NOT NULL REFERENCES preipo_scrip (id),
    intermediary_id  BIGINT         NOT NULL REFERENCES intermediary (id),
    deal_date        DATE           NOT NULL,
    quantity         NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
    client_price     NUMERIC(18, 4) NOT NULL CHECK (client_price > 0),
    vendor_price     NUMERIC(18, 4) NOT NULL CHECK (vendor_price > 0),
    markup_per_share NUMERIC(18, 4) NOT NULL CHECK (markup_per_share >= 0),
    markup_percent   NUMERIC(9, 4)  NOT NULL,
    amount           NUMERIC(18, 2) NOT NULL,
    brokerage        NUMERIC(18, 2) NOT NULL,
    gross            NUMERIC(18, 2) NOT NULL,
    gst_percent      NUMERIC(6, 3)  NOT NULL,
    gst              NUMERIC(18, 2) NOT NULL,
    invoice          NUMERIC(18, 2) NOT NULL,
    tds_applied      BOOLEAN        NOT NULL,
    tds_percent      NUMERIC(6, 3)  NOT NULL,
    tds              NUMERIC(18, 2) NOT NULL,
    net              NUMERIC(18, 2) NOT NULL,
    portal_status    VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    billing_status   VARCHAR(30)    NOT NULL DEFAULT 'TO_CONFIRM',
    deal_sheet       VARCHAR(20)    NOT NULL DEFAULT 'NA',
    remarks          VARCHAR(1000),
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by       VARCHAR(50)    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_preipo_entry_month ON preipo_entry (month);
CREATE INDEX ix_preipo_entry_client ON preipo_entry (client_id);

-- Pre-IPO brokerage carries GST at 18%; TDS 5% is deducted by the vendor (on the GST-inclusive brokerage, as in the register).
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('PREIPO', 18, 5, true, '2017-07-01', 'Starting rate, as in the Pre-IPO register', 'system');

-- Ledger: a line may come from a Pre-IPO deal (several deals of one client in a month are separate lines).
ALTER TABLE brokerage_line ADD COLUMN preipo_entry_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL
        OR loan_entry_id IS NOT NULL OR gift_entry_id IS NOT NULL OR equity_upload_id IS NOT NULL OR preipo_entry_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0),
                                                               coalesce(aif_entry_id, 0), coalesce(loan_entry_id, 0),
                                                               coalesce(gift_entry_id, 0), coalesce(preipo_entry_id, 0)) WHERE NOT voided;

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
       l.bond_entry_id,
       l.aif_entry_id,
       l.loan_entry_id,
       max(s.rm_id_at_period)                                                 AS rm_id,
       max(s.partner_id) FILTER (WHERE s.partner_role = 'SUB_BROKER')         AS sub_broker_id,
       max(s.partner_id) FILTER (WHERE s.partner_role = 'REFERRAL')           AS referral_id,
       max(s.partner_id) FILTER (WHERE s.partner_role = 'POSP')               AS posp_id,
       l.gift_entry_id,
       l.preipo_entry_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
