-- GIFT City investments entered on the GIFT CITY screen (R-82): one row per investment, as in the GIFT CITY register.
-- The amount is in USD and in rupees (worked out from the exchange rate when it is given). The brokerage is typed for now
-- (how GIFT City pays is not settled); TDS, GST, net and invoice are worked out from it like other products.
CREATE TABLE gift_entry (
    id               BIGSERIAL PRIMARY KEY,
    month            DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id            BIGINT         NOT NULL,
    partner_id       BIGINT,
    client_id        BIGINT         NOT NULL,
    mobile           VARCHAR(20),
    email            VARCHAR(200),
    address          VARCHAR(500),
    fund             VARCHAR(300)   NOT NULL,
    amount_usd       NUMERIC(16, 2) CHECK (amount_usd > 0),
    fx_rate          NUMERIC(12, 4) CHECK (fx_rate > 0),
    amount_inr       NUMERIC(16, 2) CHECK (amount_inr > 0),
    on_portal        BOOLEAN        NOT NULL DEFAULT false,
    trxn_status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    brokerage        NUMERIC(16, 2) CHECK (brokerage >= 0),
    gross            NUMERIC(16, 2) NOT NULL,
    tds_applied      BOOLEAN        NOT NULL,
    tds_percent      NUMERIC(6, 3)  NOT NULL,
    tds              NUMERIC(16, 2) NOT NULL,
    gst_percent      NUMERIC(6, 3)  NOT NULL,
    gst              NUMERIC(16, 2) NOT NULL,
    net              NUMERIC(16, 2) NOT NULL,
    invoice          NUMERIC(16, 2) NOT NULL,
    invoice_raised   BOOLEAN        NOT NULL DEFAULT false,
    payment_status   VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    received_on      DATE,
    remarks          VARCHAR(1000),
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by       VARCHAR(50)    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (amount_usd IS NOT NULL OR amount_inr IS NOT NULL)
);
CREATE INDEX ix_gift_entry_month ON gift_entry (month);
CREATE INDEX ix_gift_entry_client ON gift_entry (client_id);

-- A starting rate Finance can change on Products & taxes once GIFT City's GST / TDS treatment is settled.
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('GIFT', 18, 0, false, '2022-01-01', 'Starting rate (to confirm)', 'system');

ALTER TABLE brokerage_line ADD COLUMN gift_entry_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL
        OR loan_entry_id IS NOT NULL OR gift_entry_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0),
                                                               coalesce(aif_entry_id, 0), coalesce(loan_entry_id, 0),
                                                               coalesce(gift_entry_id, 0)) WHERE NOT voided;

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
       l.gift_entry_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
