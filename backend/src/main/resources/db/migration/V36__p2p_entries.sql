-- P2P (R-87), entry only for now: one row per P2P investment, as in the P2P register. The register has no revenue figures:
-- the brokerage is typed when known; TDS, GST, net and invoice are worked out from it. Not in month processing yet.
CREATE TABLE p2p_entry (
    id                  BIGSERIAL PRIMARY KEY,
    month               DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id               BIGINT         NOT NULL,
    partner_id          BIGINT,
    client_id           BIGINT         NOT NULL,
    mobile              VARCHAR(20),
    email               VARCHAR(200),
    address             VARCHAR(500),
    platform            VARCHAR(200)   NOT NULL,
    scheme              VARCHAR(100)   NOT NULL,
    roi_percent         NUMERIC(6, 3)  CHECK (roi_percent >= 0 AND roi_percent <= 100),
    amount              NUMERIC(16, 2) CHECK (amount > 0),
    funds_initiated_on  DATE,
    start_date          DATE,
    quarter_end_date    DATE,
    on_portal           BOOLEAN        NOT NULL DEFAULT false,
    brokerage           NUMERIC(16, 2) CHECK (brokerage >= 0),
    gross               NUMERIC(16, 2) NOT NULL,
    tds_applied         BOOLEAN        NOT NULL,
    tds_percent         NUMERIC(6, 3)  NOT NULL,
    tds                 NUMERIC(16, 2) NOT NULL,
    gst_percent         NUMERIC(6, 3)  NOT NULL,
    gst                 NUMERIC(16, 2) NOT NULL,
    net                 NUMERIC(16, 2) NOT NULL,
    invoice             NUMERIC(16, 2) NOT NULL,
    payment_note        VARCHAR(500),
    received_on         DATE,
    rev_sharing_status  VARCHAR(500),
    remarks             VARCHAR(1000),
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(50)    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50)    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_p2p_entry_month ON p2p_entry (month);
CREATE INDEX ix_p2p_entry_client ON p2p_entry (client_id);

-- P2P is a product partners can share in, with its own (starting) tax rate.
ALTER TABLE client_partner_share DROP CONSTRAINT IF EXISTS ck_cps_product;
ALTER TABLE client_partner_share ADD CONSTRAINT ck_cps_product CHECK (product_type IN
    ('MF', 'PREIPO', 'FD', 'BOND', 'LOAN', 'EQUITY', 'PMS', 'AIF', 'ADVISORY', 'GIFT', 'INS', 'NPS', 'P2P'));
ALTER TABLE partner_product_share DROP CONSTRAINT IF EXISTS partner_product_share_product_type_check;
ALTER TABLE partner_product_share ADD CONSTRAINT partner_product_share_product_type_check CHECK (product_type IN
    ('MF', 'PREIPO', 'FD', 'BOND', 'LOAN', 'EQUITY', 'PMS', 'AIF', 'ADVISORY', 'GIFT', 'INS', 'NPS', 'P2P'));
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('P2P', 18, 0, false, '2017-07-01', 'Starting rate (to confirm)', 'system');
