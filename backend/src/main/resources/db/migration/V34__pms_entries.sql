-- PMS (R-85), entry only for now: the investments (one row per client investment, as in the PMS register) and the AMC
-- payouts (one row per AMC invoice period, as kept below the register). How a payout reaches the clients and the END file
-- is decided later, so neither feeds month processing yet.
CREATE TABLE pms_investment (
    id                 BIGSERIAL PRIMARY KEY,
    month              DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    rm_id              BIGINT         NOT NULL,
    partner_id         BIGINT,
    client_id          BIGINT         NOT NULL,
    mobile             VARCHAR(20),
    email              VARCHAR(200),
    address            VARCHAR(500),
    amc                VARCHAR(200)   NOT NULL,
    strategy           VARCHAR(300)   NOT NULL,
    amount             NUMERIC(16, 2) NOT NULL CHECK (amount > 0),
    investment_date    DATE           NOT NULL,
    on_portal          BOOLEAN        NOT NULL DEFAULT false,
    trail_percent      NUMERIC(6, 3)  CHECK (trail_percent >= 0 AND trail_percent <= 100),
    payout_frequency   VARCHAR(20)    NOT NULL DEFAULT 'QUARTERLY',
    qty                NUMERIC(16, 4) CHECK (qty > 0),
    price              NUMERIC(16, 4) CHECK (price > 0),
    consideration      NUMERIC(16, 2) CHECK (consideration > 0),
    certificate        VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    investment_mail    BOOLEAN        NOT NULL DEFAULT false,
    holding_status     VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    redeemed_on        DATE,
    remarks            VARCHAR(1000),
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by         VARCHAR(50)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by         VARCHAR(50)    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (holding_status <> 'REDEEMED' OR redeemed_on IS NOT NULL)
);
CREATE INDEX ix_pms_investment_month ON pms_investment (month);
CREATE INDEX ix_pms_investment_client ON pms_investment (client_id);

CREATE TABLE pms_payout (
    id              BIGSERIAL PRIMARY KEY,
    amc             VARCHAR(200)   NOT NULL,
    period_from     DATE           NOT NULL CHECK (extract(day FROM period_from) = 1),
    period_to       DATE           NOT NULL CHECK (extract(day FROM period_to) = 1),
    amount          NUMERIC(16, 2) NOT NULL CHECK (amount >= 0),
    tds_applied     BOOLEAN        NOT NULL,
    tds_percent     NUMERIC(6, 3)  NOT NULL,
    tds             NUMERIC(16, 2) NOT NULL,
    gst_percent     NUMERIC(6, 3)  NOT NULL,
    gst             NUMERIC(16, 2) NOT NULL,
    net             NUMERIC(16, 2) NOT NULL,
    invoice         NUMERIC(16, 2) NOT NULL,
    invoice_raised  BOOLEAN        NOT NULL DEFAULT false,
    received_on     DATE,
    remarks         VARCHAR(1000),
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(50)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50)    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (period_to >= period_from)
);
CREATE INDEX ix_pms_payout_period ON pms_payout (period_from);

-- PMS trail commission: GST 18%, TDS as the AMC deducts it (the payout form starts from this, changeable).
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('PMS', 18, 2, true, '2022-01-01', 'Starting rate (to confirm)', 'system');
