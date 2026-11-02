-- PROPOSAL (design only, not a migration): one holdings model for every product, built from what the FD, bond, AIF,
-- loan, GIFT City, P2P, NPS, PMS and equity screens record today. Plan: docs/design/db-rework-plan.md.
--
-- asset              one row per holding of one client (PAN): what, where, how much, worth, status, who booked it.
-- asset_terms        1:1 for fixed-income holdings (FD, bond, P2P, loan): rate, payouts, compounding, tenure, maturity.
-- asset_transaction  what happened to it: purchase, SIP, redemption, AIF drawdown, interest, maturity, loan disbursal.
-- asset_holder       joint holders, nominees, a minor's guardian, by PAN.
-- asset_check        the servicing ticks (certificate, on portal, welcome mail…), one row per tick.
-- asset_snapshot     value per holding per month: AUM by RM / branch / partner over time.
-- product (+ product_price), and 5 masters: product_type, asset_class, provider, frequency, servicing_check.
-- revenue            one commission earned, any product; brokerage_line.revenue_id points at it.
-- client_contact     mobile, e-mail, address once per client instead of on every entry.
--
-- Reuses: client, employee (RM), partner (with its ARN / POSP code), intermediary (platform), upload, exchange_rate.
-- Money is NUMERIC; amounts are rupees unless the column says _fc (the holding's own currency).

-- ── Masters ─────────────────────────────────────────────────────────────────────────────────────────────────────────

-- MF, EQUITY, FD, BOND, AIF, PMS, NPS, P2P, GIFT, LOAN… The family decides which details a holding of it has.
CREATE TABLE product_type (
    id          SMALLSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    family      VARCHAR(20)  NOT NULL CHECK (family IN ('MARKET', 'FIXED_INCOME', 'COMMITMENT', 'LIABILITY')),
    uses_units  BOOLEAN      NOT NULL,      -- MF, equity, PMS, AIF, bonds: yes; FD, P2P, loan: no
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Equity › Large cap, Debt › Corporate bond, Alternative › Cat II AIF… one table, a sub-class points at its class.
CREATE TABLE asset_class (
    id        SMALLSERIAL PRIMARY KEY,
    parent_id SMALLINT     REFERENCES asset_class (id),
    name      VARCHAR(100) NOT NULL,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (parent_id, name)
);

-- Whoever issues or manages a product: AMC, FD company, bond issuer, AIF / PMS manager, lender, NPS fund manager.
CREATE TABLE provider (
    id     BIGSERIAL PRIMARY KEY,
    type   VARCHAR(20)  NOT NULL CHECK (type IN ('AMC', 'FD_COMPANY', 'ISSUER', 'AIF_MANAGER', 'PMS_MANAGER', 'LENDER', 'NPS_MANAGER', 'OTHER')),
    name   VARCHAR(200) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_provider ON provider (type, lower(name));

-- Monthly, Quarterly, Half-yearly, Yearly, On maturity… for payouts, compounding and trail.
CREATE TABLE frequency (
    id             SMALLSERIAL PRIMARY KEY,
    name           VARCHAR(50) NOT NULL UNIQUE,
    times_per_year SMALLINT    CHECK (times_per_year BETWEEN 1 AND 365)   -- null: on maturity
);

-- The ticks Ops keeps per holding (today a column each on every screen): certificate, on portal, welcome mail,
-- investment mail, SOA received, application form. product_type_id null = asked for every product.
CREATE TABLE servicing_check (
    id              SMALLSERIAL PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    product_type_id SMALLINT     REFERENCES product_type (id),
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ── What can be held ────────────────────────────────────────────────────────────────────────────────────────────────

-- The scheme, FD scheme, bond, fund, strategy or loan product, stored once for every client who holds it.
CREATE TABLE product (
    id              BIGSERIAL PRIMARY KEY,
    product_type_id SMALLINT       NOT NULL REFERENCES product_type (id),
    asset_class_id  SMALLINT       REFERENCES asset_class (id),
    provider_id     BIGINT         REFERENCES provider (id),
    name            VARCHAR(300)   NOT NULL,
    isin            VARCHAR(12)    UNIQUE,
    scheme_code     VARCHAR(30),                   -- AMFI code / RTA scheme code
    finesse_code    VARCHAR(50),
    currency        CHAR(3)        NOT NULL DEFAULT 'INR',
    -- set only for a listed bond: the same for every holder
    face_value      NUMERIC(18, 2),
    coupon_pct      NUMERIC(7, 4),
    maturity_date   DATE,
    active          BOOLEAN        NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_product_type ON product (product_type_id);
CREATE UNIQUE INDEX ux_product_name ON product (product_type_id, provider_id, lower(name));

-- NAV / market price by day, for holdings with units.
CREATE TABLE product_price (
    product_id BIGINT         NOT NULL REFERENCES product (id),
    price_date DATE           NOT NULL,
    price      NUMERIC(18, 6) NOT NULL,
    source     VARCHAR(30)    NOT NULL,            -- AMFI, exchange, platform file, typed
    PRIMARY KEY (product_id, price_date)
);

-- ── The holding ─────────────────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE asset (
    id                 BIGSERIAL PRIMARY KEY,
    -- whose and what
    client_id          BIGINT         NOT NULL REFERENCES client (id),         -- first holder: the PAN it shows under
    product_id         BIGINT         NOT NULL REFERENCES product (id),
    product_type_id    SMALLINT       NOT NULL REFERENCES product_type (id),   -- copy of the product's, for fast filters
    account_ref        VARCHAR(50),                    -- folio, FD no., bond / AIF / PMS account, PRAN, loan account
    holding_mode       VARCHAR(20)    NOT NULL DEFAULT 'SINGLE' CHECK (holding_mode IN ('SINGLE', 'JOINT', 'ANYONE_OR_SURVIVOR')),
    -- who brought it and through where
    rm_id              BIGINT         NOT NULL REFERENCES employee (id),       -- the RM it was booked by
    partner_id         BIGINT         REFERENCES partner (id),                 -- sub broker / referral / POSP on it
    platform_id        BIGINT         REFERENCES intermediary (id),            -- GoldenPi, SMC, the FD platform…
    held_away          BOOLEAN        NOT NULL DEFAULT FALSE,                  -- held outside our code / platform
    -- life
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                                      CHECK (status IN ('PENDING', 'ACTIVE', 'MATURED', 'REDEEMED', 'CLOSED', 'CANCELLED')),
    start_date         DATE           NOT NULL,        -- deposit / investment / allotment / agreement / disbursal date
    maturity_date      DATE,
    closed_on          DATE,
    -- how much (rupees; _fc = the product's own currency, for GIFT City)
    currency           CHAR(3)        NOT NULL DEFAULT 'INR',
    committed_amount   NUMERIC(18, 2),                 -- AIF commitment, loan sanctioned
    invested_amount    NUMERIC(18, 2) NOT NULL,        -- cost of what is held: drawn, deposited, utilised
    invested_amount_fc NUMERIC(18, 2),
    units              NUMERIC(20, 6),
    avg_cost           NUMERIC(18, 6),                 -- per unit
    -- worth (refreshed from prices or files; maturity-value products accrue)
    current_value      NUMERIC(18, 2),
    value_as_of        DATE,
    value_source       VARCHAR(20)    CHECK (value_source IN ('PRICE', 'FILE', 'ACCRUAL', 'TYPED')),
    -- what Epsilon earns on it, when not the product's / platform's default (e.g. a PMS trail %)
    commission_pct     NUMERIC(7, 4),
    commission_freq_id SMALLINT       REFERENCES frequency (id),
    -- where it came from
    source             VARCHAR(10)    NOT NULL CHECK (source IN ('SCREEN', 'FILE', 'MIGRATED')),
    upload_id          BIGINT         REFERENCES upload (id),
    legacy_product     VARCHAR(20),                    -- FD, BOND… and the old entry's id, while those tables exist
    legacy_id          BIGINT,
    remarks            VARCHAR(1000),
    created_by         VARCHAR(50)    NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by         VARCHAR(50),
    updated_at         TIMESTAMPTZ,
    CHECK (closed_on IS NULL OR status IN ('MATURED', 'REDEEMED', 'CLOSED', 'CANCELLED')),
    CHECK (currency = 'INR' OR invested_amount_fc IS NOT NULL)
);
CREATE INDEX ix_asset_client ON asset (client_id, status);
CREATE INDEX ix_asset_rm ON asset (rm_id, status);
CREATE INDEX ix_asset_type ON asset (product_type_id, status);
CREATE INDEX ix_asset_product ON asset (product_id);
CREATE INDEX ix_asset_maturing ON asset (maturity_date) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX ux_asset_legacy ON asset (legacy_product, legacy_id) WHERE legacy_id IS NOT NULL;

-- Fixed-income terms (FD, bond, P2P, loan): today spread over rate_percent, coupon_percent, roi_percent,
-- interest_frequency, payout_frequency, compounding, period_months, maturity_value, payment_schedule, ytm_percent.
CREATE TABLE asset_terms (
    asset_id            BIGINT         PRIMARY KEY REFERENCES asset (id),
    rate_pct            NUMERIC(7, 4)  NOT NULL,        -- interest / coupon / ROI / loan rate
    payout_freq_id      SMALLINT       REFERENCES frequency (id),   -- null: paid at maturity (cumulative)
    compounding_freq_id SMALLINT       REFERENCES frequency (id),   -- null: simple interest
    tenure_months       SMALLINT,
    maturity_amount     NUMERIC(18, 2),
    ytm_pct             NUMERIC(7, 4),                  -- bonds bought on the market
    accrued_interest    NUMERIC(18, 2),                 -- paid at purchase (bonds)
    payout_schedule     VARCHAR(300),
    next_payout_date    DATE
);

-- Everything that moved money or units: today's AIF instalments, PMS / bond purchases, redemptions, payouts.
CREATE TABLE asset_transaction (
    id         BIGSERIAL PRIMARY KEY,
    asset_id   BIGINT         NOT NULL REFERENCES asset (id),
    type       VARCHAR(20)    NOT NULL CHECK (type IN ('PURCHASE', 'SIP', 'ADDITIONAL', 'DRAWDOWN', 'REDEMPTION', 'SWITCH_IN',
                                                      'SWITCH_OUT', 'INTEREST', 'DIVIDEND', 'MATURITY', 'DISBURSAL', 'REPAYMENT')),
    txn_date   DATE           NOT NULL,
    units      NUMERIC(20, 6),
    price      NUMERIC(18, 6),
    amount     NUMERIC(18, 2) NOT NULL,                 -- rupees
    amount_fc  NUMERIC(18, 2),
    fx_rate    NUMERIC(12, 6),                          -- the exchange_rate used, kept as used
    upload_id  BIGINT         REFERENCES upload (id),
    created_by VARCHAR(50)    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_asset_transaction ON asset_transaction (asset_id, txn_date);

-- Joint holders, nominees and a minor's guardian, each a client (PAN): a holding shows under every PAN on it.
CREATE TABLE asset_holder (
    asset_id  BIGINT        NOT NULL REFERENCES asset (id),
    client_id BIGINT        NOT NULL REFERENCES client (id),
    role      VARCHAR(20)   NOT NULL CHECK (role IN ('JOINT', 'NOMINEE', 'GUARDIAN')),
    seq       SMALLINT      NOT NULL DEFAULT 1,
    share_pct NUMERIC(6, 3) CHECK (share_pct > 0 AND share_pct <= 100),
    PRIMARY KEY (asset_id, client_id, role)
);
CREATE INDEX ix_asset_holder_client ON asset_holder (client_id);

-- One servicing tick on one holding.
CREATE TABLE asset_check (
    asset_id BIGINT      NOT NULL REFERENCES asset (id),
    check_id SMALLINT    NOT NULL REFERENCES servicing_check (id),
    done_on  DATE        NOT NULL,
    done_by  VARCHAR(50) NOT NULL,
    PRIMARY KEY (asset_id, check_id)
);

-- Each holding's position at each month end, kept once the month is closed: AUM trends by RM, branch, partner.
CREATE TABLE asset_snapshot (
    asset_id        BIGINT         NOT NULL REFERENCES asset (id),
    month           DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    invested_amount NUMERIC(18, 2) NOT NULL,
    current_value   NUMERIC(18, 2) NOT NULL,
    units           NUMERIC(20, 6),
    PRIMARY KEY (asset_id, month)
);
CREATE INDEX ix_asset_snapshot_month ON asset_snapshot (month);

-- ── Money ───────────────────────────────────────────────────────────────────────────────────────────────────────────

-- One commission earned, for any product (today's gross / TDS / GST / net / invoice / payment columns on nine tables).
-- Usually for one holding; a provider's payout for a period (today's pms_payout) has no client.
CREATE TABLE revenue (
    id                   BIGSERIAL PRIMARY KEY,
    month                DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    product_type_id      SMALLINT       NOT NULL REFERENCES product_type (id),
    asset_id             BIGINT         REFERENCES asset (id),
    asset_transaction_id BIGINT         REFERENCES asset_transaction (id),
    client_id            BIGINT         REFERENCES client (id),
    provider_id          BIGINT         REFERENCES provider (id),
    period_from          DATE,
    period_to            DATE,
    basis_amount         NUMERIC(18, 2),                -- what the % applies to
    rate_pct             NUMERIC(7, 4),
    gross                NUMERIC(18, 2) NOT NULL,
    tds_pct              NUMERIC(6, 3),
    tds                  NUMERIC(18, 2) NOT NULL DEFAULT 0,
    gst_pct              NUMERIC(6, 3),
    gst                  NUMERIC(18, 2) NOT NULL DEFAULT 0,
    net                  NUMERIC(18, 2) NOT NULL,
    invoice              NUMERIC(18, 2) NOT NULL,
    figures_as_given     BOOLEAN        NOT NULL DEFAULT FALSE,
    invoice_no           VARCHAR(50),
    invoice_date         DATE,
    payment_status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING', 'RECEIVED', 'NOT_DUE')),
    received_on          DATE,
    status               VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    remarks              VARCHAR(1000),
    legacy_product       VARCHAR(20),
    legacy_id            BIGINT,
    created_by           VARCHAR(50)    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by           VARCHAR(50),
    updated_at           TIMESTAMPTZ,
    CHECK (client_id IS NOT NULL OR provider_id IS NOT NULL)
);
CREATE INDEX ix_revenue_month ON revenue (month, product_type_id);
CREATE INDEX ix_revenue_client ON revenue (client_id);
CREATE UNIQUE INDEX ux_revenue_legacy ON revenue (legacy_product, legacy_id) WHERE legacy_id IS NOT NULL;

-- New ledger lines name their revenue; old lines keep their *_entry_id (the ledger is append-only).
ALTER TABLE brokerage_line ADD COLUMN revenue_id BIGINT;

-- ── Client ──────────────────────────────────────────────────────────────────────────────────────────────────────────

-- Typed once per client, not on every FD / bond / AIF / NPS… entry. Encrypted like the PAN.
CREATE TABLE client_contact (
    client_id         BIGINT       PRIMARY KEY REFERENCES client (id),
    mobile_encrypted  VARCHAR(500),
    email_encrypted   VARCHAR(500),
    address_encrypted VARCHAR(2000),
    updated_by        VARCHAR(50)  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
