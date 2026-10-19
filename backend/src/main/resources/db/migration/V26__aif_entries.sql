-- AIF commitments entered on the AIF screen (R-78): one row per commitment, its drawdown instalments, and an optional
-- revenue-sharing % agreed for this deal with its referral / sub-broker (used instead of the partner's AIF %).
CREATE TABLE aif_entry (
    id                  BIGSERIAL PRIMARY KEY,
    month               DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id               BIGINT         NOT NULL,
    partner_id          BIGINT,
    sharing_percent     NUMERIC(6, 3) CHECK (sharing_percent IS NULL OR (sharing_percent >= 0 AND sharing_percent <= 100)),
    client_id           BIGINT         NOT NULL,
    mobile              VARCHAR(20),
    email               VARCHAR(200),
    address             VARCHAR(500),
    intermediary_id     BIGINT         NOT NULL REFERENCES intermediary (id),
    fund_name           VARCHAR(300)   NOT NULL,
    commitment          NUMERIC(18, 2) NOT NULL CHECK (commitment > 0),
    agreement_date      DATE,
    tenure              VARCHAR(100),
    maturity_date       DATE,
    soa_received        BOOLEAN        NOT NULL DEFAULT false,
    application_form    BOOLEAN        NOT NULL DEFAULT false,
    investment_mail     BOOLEAN        NOT NULL DEFAULT false,
    quantity            NUMERIC(18, 4),
    price               NUMERIC(18, 4),
    certificate         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    payout_terms        VARCHAR(300),
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
    partner_paid        BOOLEAN        NOT NULL DEFAULT false,
    on_investwell       BOOLEAN        NOT NULL DEFAULT false,
    remarks             VARCHAR(1000),
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50)    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50)    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_aif_entry_month ON aif_entry (month);

CREATE TABLE aif_instalment (
    id           BIGSERIAL PRIMARY KEY,
    aif_entry_id BIGINT         NOT NULL REFERENCES aif_entry (id),
    seq          INT            NOT NULL,
    amount       NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    paid_on      DATE,
    UNIQUE (aif_entry_id, seq)
);

INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('AIF', 18, 0, false, '2022-01-01', 'Starting rate', 'system');

ALTER TABLE brokerage_line ADD COLUMN aif_entry_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0),
                                                               coalesce(aif_entry_id, 0)) WHERE NOT voided;

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
       l.aif_entry_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
