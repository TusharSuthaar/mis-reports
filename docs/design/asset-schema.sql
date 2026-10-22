-- PROPOSAL (design only, not a migration yet): one holdings model for every product.
--
-- asset          one row per holding of one client (PAN): what is held, how much, what it is worth, how it has done.
--                Lists, totals and the client's PAN-level holdings read this table alone.
-- asset_fd / asset_bond / asset_insurance
--                1:1 details only those products have, read when one holding is opened.
-- asset_holder / asset_goal
--                joins: joint holders, nominees and guardians by PAN; goals an asset is earmarked for.
-- masters        product_type, asset_category, asset_sub_category, provider, product, product_nav, frequency, arn,
--                depository_participant, policy_type, policy_sub_type, policy_status, business_type, goal.
--
-- Reuses what exists: client (one per PAN), partner (sub broker), intermediary (FD / bond platform), upload (the file a
-- row came from), client_external_id (the client's BSE UCC). Money is NUMERIC, never float.
-- Naming follows this repo: each table's key is "id"; asset.id is the "asset_id" of the source layout.

-- ── Masters ─────────────────────────────────────────────────────────────────────────────────────────────────────────

-- MF, EQUITY, BOND, FD, INSURANCE, AIF, PMS, NPS, P2P, GIFT, ETF, GOLD…
CREATE TABLE product_type (
    id   SMALLSERIAL PRIMARY KEY,
    code VARCHAR(20)  NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

-- Equity, Debt, Hybrid, Gold, Real estate, Alternative, Protection…
CREATE TABLE asset_category (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

-- Large cap, Liquid, Corporate bond, Term plan… always under one category.
CREATE TABLE asset_sub_category (
    id          SMALLSERIAL PRIMARY KEY,
    category_id SMALLINT     NOT NULL REFERENCES asset_category (id),
    name        VARCHAR(100) NOT NULL,
    UNIQUE (category_id, name)
);

-- Who issues or manages the product: an AMC, an insurer, a bond issuer, an FD company, a PMS / AIF manager.
CREATE TABLE provider (
    id                BIGSERIAL PRIMARY KEY,
    type              VARCHAR(20)  NOT NULL CHECK (type IN ('AMC', 'INSURER', 'ISSUER', 'FD_COMPANY', 'PMS_MANAGER', 'AIF_MANAGER', 'OTHER')),
    name              VARCHAR(200) NOT NULL,
    issuer_type       VARCHAR(50),                 -- PSU, NBFC, Bank, Corporate… (bond issuers)
    industry_category VARCHAR(100),
    logo_url          VARCHAR(500)
);
CREATE UNIQUE INDEX ux_provider_type_name ON provider (type, lower(name));

-- Monthly, Quarterly, Half-yearly, Yearly, Cumulative, On maturity… shared by interest, accrual and premium frequencies.
CREATE TABLE frequency (
    id              SMALLSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE,
    times_per_year  SMALLINT                    -- null for cumulative / on maturity
);

-- The security itself, shared by every client who holds it: scheme, bond, FD scheme, policy plan, stock.
CREATE TABLE product (
    id                  BIGSERIAL PRIMARY KEY,
    product_type_id     SMALLINT      NOT NULL REFERENCES product_type (id),
    asset_category_id   SMALLINT      REFERENCES asset_category (id),
    asset_sub_cat_id    SMALLINT      REFERENCES asset_sub_category (id),
    provider_id         BIGINT        REFERENCES provider (id),
    name                VARCHAR(300)  NOT NULL,
    isin                VARCHAR(12)   UNIQUE,
    amfi_code           VARCHAR(20)   UNIQUE,
    scheme_plan         VARCHAR(20)   CHECK (scheme_plan IN ('DIRECT', 'REGULAR')),
    accord_scheme_code  VARCHAR(30),
    accord_scheme_name  VARCHAR(300),
    mfit_scheme_id      VARCHAR(30),
    finesse_code        VARCHAR(50),
    finesse_name        VARCHAR(300),
    -- bond / debt terms (same for every holder)
    coupon_rate         NUMERIC(7, 4),
    face_value          NUMERIC(18, 2),
    issue_date          DATE,
    maturity_date       DATE,
    call_date           DATE,
    interest_freq_id    SMALLINT      REFERENCES frequency (id),
    credit_rating       VARCHAR(20),
    bond_type           VARCHAR(50),
    bond_asset_class    VARCHAR(50),
    is_taxable          BOOLEAN,
    is_listed           BOOLEAN,
    is_secured          BOOLEAN,
    -- latest price, kept here so lists need no join to product_nav
    nav                 NUMERIC(18, 6),
    nav_date            DATE,
    previous_nav        NUMERIC(18, 6),
    previous_nav_date   DATE,
    active              BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_product_type ON product (product_type_id);
CREATE INDEX ix_product_finesse ON product (finesse_code);

-- Price history: one NAV / price per product per day. product.nav is the latest row.
CREATE TABLE product_nav (
    product_id BIGINT         NOT NULL REFERENCES product (id),
    nav_date   DATE           NOT NULL,
    nav        NUMERIC(18, 6) NOT NULL,
    PRIMARY KEY (product_id, nav_date)
);

-- ARNs business is booked under (the house ARN, a sub broker's own ARN).
CREATE TABLE arn (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,   -- ARN-12345
    holder_name VARCHAR(200) NOT NULL,
    partner_id  BIGINT       REFERENCES partner (id),   -- null = the house ARN
    valid_to    DATE
);

CREATE TABLE depository_participant (
    id      BIGSERIAL PRIMARY KEY,
    dp_code VARCHAR(20)  NOT NULL UNIQUE,   -- IN300xxx / 12xxxxxx
    name    VARCHAR(200) NOT NULL
);

-- Insurance: Life → Term / ULIP / Endowment; Health → Individual / Floater; Motor → Private car / Two-wheeler…
CREATE TABLE policy_type (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE policy_sub_type (
    id             SMALLSERIAL PRIMARY KEY,
    policy_type_id SMALLINT     NOT NULL REFERENCES policy_type (id),
    name           VARCHAR(100) NOT NULL,
    UNIQUE (policy_type_id, name)
);

-- In force, Lapsed, Paid up, Surrendered, Matured, Claimed…
CREATE TABLE policy_status (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- New, Renewal, Portability, Rollover…
CREATE TABLE business_type (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- A client's goal (retirement, child's education…), at PAN level.
CREATE TABLE goal (
    id            BIGSERIAL PRIMARY KEY,
    client_id     BIGINT        NOT NULL REFERENCES client (id),
    name          VARCHAR(200)  NOT NULL,
    target_amount NUMERIC(18, 2),
    target_date   DATE
);
CREATE INDEX ix_goal_client ON goal (client_id);

-- ── The holding ─────────────────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE asset (
    id                        BIGSERIAL PRIMARY KEY,
    client_id                 BIGINT         NOT NULL REFERENCES client (id),   -- the PAN: first holder
    product_id                BIGINT         NOT NULL REFERENCES product (id),
    product_type_id           SMALLINT       NOT NULL REFERENCES product_type (id),   -- copy of product's, for fast filters
    status                    VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                                             CHECK (status IN ('ACTIVE', 'REDEEMED', 'MATURED', 'SURRENDERED', 'LAPSED', 'CLOSED')),
    held_away                 BOOLEAN        NOT NULL DEFAULT FALSE,   -- held outside our ARN / platform
    -- where it is held
    folio_no                  VARCHAR(50),                             -- MF folio, policy / FD / account number
    arn_id                    BIGINT         REFERENCES arn (id),
    sub_broker_id             BIGINT         REFERENCES partner (id),
    dp_id                     BIGINT         REFERENCES depository_participant (id),
    platform_id               BIGINT         REFERENCES intermediary (id),   -- FD partner / bond platform
    -- quantity and cost
    units                     NUMERIC(20, 6),
    avg_purchase_price        NUMERIC(18, 6),
    investment_date           DATE,
    investment_value          NUMERIC(18, 2) NOT NULL DEFAULT 0,   -- cost of what is still held
    total_amount              NUMERIC(18, 2),                      -- everything ever invested
    redeemed_amount           NUMERIC(18, 2),
    weighted_avg_days         INTEGER,
    -- value and returns: a daily snapshot, so lists and totals never recompute
    current_value             NUMERIC(18, 2),
    current_value_date        DATE,
    previous_current_value    NUMERIC(18, 2),
    one_day_change_amount     NUMERIC(18, 2),
    one_day_change_pct        NUMERIC(9, 4),
    realized_profit           NUMERIC(18, 2),
    unrealized_profit         NUMERIC(18, 2),
    dividend_reinvested       NUMERIC(18, 2),
    dividend_paid_out         NUMERIC(18, 2),
    total_returns             NUMERIC(18, 2),
    abs_return_pct            NUMERIC(9, 4),
    xirr_pct                  NUMERIC(9, 4),
    maturity_date             DATE,                                -- FD, bond, policy: for "maturing soon"
    closed_on                 DATE,                                -- sold out / redeemed / matured
    replaced_by_asset_id      BIGINT         REFERENCES asset (id),   -- renewal, switch, rollover
    -- where the row came from
    upload_id                 BIGINT         REFERENCES upload (id),
    file_record_no            INTEGER,
    finesse_request_id        VARCHAR(50),
    created_by                VARCHAR(50)    NOT NULL,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by                VARCHAR(50),
    updated_at                TIMESTAMPTZ
);
CREATE INDEX ix_asset_client ON asset (client_id, status);
CREATE INDEX ix_asset_product ON asset (product_id);
CREATE INDEX ix_asset_type ON asset (product_type_id, status);
CREATE INDEX ix_asset_maturity ON asset (maturity_date) WHERE status = 'ACTIVE' AND maturity_date IS NOT NULL;
CREATE UNIQUE INDEX ux_asset_folio ON asset (client_id, product_id, folio_no) WHERE folio_no IS NOT NULL AND status = 'ACTIVE';

-- ── Details only some products have (1:1, key = asset id) ───────────────────────────────────────────────────────────

CREATE TABLE asset_fd (
    asset_id                 BIGINT         PRIMARY KEY REFERENCES asset (id) ON DELETE CASCADE,
    interest_rate            NUMERIC(7, 4)  NOT NULL,
    interest_payout_freq_id  SMALLINT       REFERENCES frequency (id),
    accrual_freq_id          SMALLINT       REFERENCES frequency (id),
    is_cumulative            BOOLEAN        NOT NULL,
    cumulative_type          VARCHAR(30),
    tenure_years             SMALLINT       NOT NULL DEFAULT 0,
    tenure_months            SMALLINT       NOT NULL DEFAULT 0,
    tenure_days              SMALLINT       NOT NULL DEFAULT 0,
    maturity_amount          NUMERIC(18, 2),
    interest_payment_schedule VARCHAR(200),
    accrued_interest         NUMERIC(18, 2),
    interest_paid            NUMERIC(18, 2)
);

CREATE TABLE asset_bond (
    asset_id                 BIGINT         PRIMARY KEY REFERENCES asset (id) ON DELETE CASCADE,
    ytm_ytc                  NUMERIC(7, 4),
    next_interest_date       DATE,
    last_interest_date       DATE,
    accrued_interest         NUMERIC(18, 2),
    interest_paid            NUMERIC(18, 2)
);

CREATE TABLE asset_insurance (
    asset_id                 BIGINT         PRIMARY KEY REFERENCES asset (id) ON DELETE CASCADE,
    policy_number            VARCHAR(50)    NOT NULL,
    policy_holder_name       VARCHAR(200),
    policy_type_id           SMALLINT       REFERENCES policy_type (id),
    policy_sub_type_id       SMALLINT       REFERENCES policy_sub_type (id),
    policy_status_id         SMALLINT       REFERENCES policy_status (id),
    business_type_id         SMALLINT       REFERENCES business_type (id),
    premium                  NUMERIC(18, 2),
    premium_freq_id          SMALLINT       REFERENCES frequency (id),
    premium_payment_term     SMALLINT,                             -- years
    payment_schedule         VARCHAR(200),
    sum_assured              NUMERIC(18, 2),
    policy_start_date        DATE,
    policy_term              SMALLINT,                             -- years
    renewal_date             DATE,
    last_renewal_processed   DATE,
    grace_period_end_date    DATE,
    vehicle_no               VARCHAR(20),
    document_id              BIGINT                                -- the policy document in the file store
);
CREATE INDEX ix_asset_insurance_renewal ON asset_insurance (renewal_date);

-- ── Joins ───────────────────────────────────────────────────────────────────────────────────────────────────────────

-- Everyone on a holding, by PAN: the first holder (also asset.client_id), joint holders, nominees, a minor's guardian.
-- This is what puts a joint or minor's holding under every PAN it belongs to.
CREATE TABLE asset_holder (
    asset_id     BIGINT      NOT NULL REFERENCES asset (id) ON DELETE CASCADE,
    client_id    BIGINT      NOT NULL REFERENCES client (id),
    role         VARCHAR(20) NOT NULL CHECK (role IN ('FIRST', 'JOINT', 'NOMINEE', 'GUARDIAN')),
    holder_order SMALLINT    NOT NULL DEFAULT 1,
    share_pct    NUMERIC(6, 3) CHECK (share_pct > 0 AND share_pct <= 100),   -- nominees
    PRIMARY KEY (asset_id, client_id, role)
);
CREATE INDEX ix_asset_holder_client ON asset_holder (client_id);

-- An asset can be earmarked for several goals, part of its value each.
CREATE TABLE asset_goal (
    asset_id       BIGINT        NOT NULL REFERENCES asset (id) ON DELETE CASCADE,
    goal_id        BIGINT        NOT NULL REFERENCES goal (id),
    allocation_pct NUMERIC(6, 3) NOT NULL DEFAULT 100 CHECK (allocation_pct > 0 AND allocation_pct <= 100),
    PRIMARY KEY (asset_id, goal_id)
);
