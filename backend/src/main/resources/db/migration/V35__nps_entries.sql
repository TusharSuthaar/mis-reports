-- NPS (R-86), entry only for now: one row per NPS investment, as in the NPS register, with its revenue when there is one
-- (the invoice amount without GST, typed or POP charges × sharing %; GST shown as CGST + SGST). Not in month processing yet.
CREATE TABLE nps_entry (
    id               BIGSERIAL PRIMARY KEY,
    month            DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id            BIGINT         NOT NULL,
    partner_id       BIGINT,
    client_id        BIGINT         NOT NULL,
    mobile           VARCHAR(20),
    email            VARCHAR(200),
    address          VARCHAR(500),
    pop              VARCHAR(200)   NOT NULL,
    investment_date  DATE           NOT NULL,
    amount           NUMERIC(16, 2) NOT NULL CHECK (amount > 0),
    welcome_mail     BOOLEAN        NOT NULL DEFAULT false,
    investment_mail  BOOLEAN        NOT NULL DEFAULT false,
    certificate      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    on_portal        BOOLEAN        NOT NULL DEFAULT false,
    revenue_remarks  VARCHAR(500),
    pop_charges      NUMERIC(16, 2) CHECK (pop_charges >= 0),
    share_percent    NUMERIC(6, 3)  CHECK (share_percent >= 0 AND share_percent <= 100),
    revenue          NUMERIC(16, 2) NOT NULL DEFAULT 0 CHECK (revenue >= 0),
    gst_percent      NUMERIC(6, 3)  NOT NULL,
    gst              NUMERIC(16, 2) NOT NULL,
    invoice          NUMERIC(16, 2) NOT NULL,
    received_on      DATE,
    remarks          VARCHAR(1000),
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by       VARCHAR(50)    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_nps_entry_month ON nps_entry (month);
CREATE INDEX ix_nps_entry_client ON nps_entry (client_id);

-- NPS is a product partners can share in, and has its own GST rate (CGST 9% + SGST 9%).
ALTER TABLE client_partner_share DROP CONSTRAINT IF EXISTS ck_cps_product;
ALTER TABLE client_partner_share ADD CONSTRAINT ck_cps_product CHECK (product_type IN
    ('MF', 'PREIPO', 'FD', 'BOND', 'LOAN', 'EQUITY', 'PMS', 'AIF', 'ADVISORY', 'GIFT', 'INS', 'NPS'));
ALTER TABLE partner_product_share DROP CONSTRAINT IF EXISTS partner_product_share_product_type_check;
ALTER TABLE partner_product_share ADD CONSTRAINT partner_product_share_product_type_check CHECK (product_type IN
    ('MF', 'PREIPO', 'FD', 'BOND', 'LOAN', 'EQUITY', 'PMS', 'AIF', 'ADVISORY', 'GIFT', 'INS', 'NPS'));
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('NPS', 18, 0, false, '2017-07-01', 'CGST 9% + SGST 9%', 'system');
