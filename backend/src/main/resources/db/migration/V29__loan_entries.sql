-- Loans arranged through Epsilon, entered on the Loans screen: one row per payout (R-80). A disbursal payout is the
-- lender's payout % of the sanctioned (or utilised) amount; an LAS trail payout repeats each quarter on the utilised
-- amount, each as its own entry in its own month. Figures are stored as worked out on save (or as the register gives them).
CREATE TABLE loan_entry (
    id                BIGSERIAL PRIMARY KEY,
    month             DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id             BIGINT         NOT NULL,
    partner_id        BIGINT,
    client_id         BIGINT         NOT NULL,
    mobile            VARCHAR(20),
    email             VARCHAR(200),
    address           VARCHAR(500),
    lender            VARCHAR(200)   NOT NULL,
    loan_type         VARCHAR(100)   NOT NULL,
    disbursal_date    DATE           NOT NULL,
    sanctioned_amount NUMERIC(16, 2) NOT NULL CHECK (sanctioned_amount > 0),
    utilised_amount   NUMERIC(16, 2) CHECK (utilised_amount >= 0),
    rate_percent      NUMERIC(6, 3)  CHECK (rate_percent >= 0 AND rate_percent <= 100),
    payout_kind       VARCHAR(20)    NOT NULL,
    payout_on         VARCHAR(20)    NOT NULL,
    payout_percent    NUMERIC(6, 3)  CHECK (payout_percent >= 0 AND payout_percent <= 100),
    fixed_amount      NUMERIC(16, 2) CHECK (fixed_amount >= 0),
    gross             NUMERIC(16, 2) NOT NULL,
    tds_applied       BOOLEAN        NOT NULL,
    tds_percent       NUMERIC(6, 3)  NOT NULL,
    tds               NUMERIC(16, 2) NOT NULL,
    gst_percent       NUMERIC(6, 3)  NOT NULL,
    gst               NUMERIC(16, 2) NOT NULL,
    net               NUMERIC(16, 2) NOT NULL,
    invoice           NUMERIC(16, 2) NOT NULL,
    figures_as_given  BOOLEAN        NOT NULL DEFAULT false,
    invoice_raised    BOOLEAN        NOT NULL DEFAULT false,
    payment_status    VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    payment_note      VARCHAR(500),
    received_on       DATE,
    loan_closed       BOOLEAN        NOT NULL DEFAULT false,
    remarks           VARCHAR(1000),
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by        VARCHAR(50)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_loan_entry_month ON loan_entry (month);
CREATE INDEX ix_loan_entry_client ON loan_entry (client_id);

-- Loan commission: GST 18%; TDS on commission 5% until 30 Sep 2024, 2% from 1 Oct 2024. An entry may use another TDS %.
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, valid_to, reason, changed_by)
VALUES ('LOAN', 18, 5, true, '2017-07-01', '2024-09-30', 'TDS on commission 5%', 'system'),
       ('LOAN', 18, 2, true, '2024-10-01', NULL, 'TDS on commission 2% from 1 Oct 2024', 'system');

ALTER TABLE brokerage_line ADD COLUMN loan_entry_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL
        OR loan_entry_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0),
                                                               coalesce(aif_entry_id, 0), coalesce(loan_entry_id, 0)) WHERE NOT voided;

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
       l.loan_entry_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
