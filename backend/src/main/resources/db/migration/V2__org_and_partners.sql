-- Phase 1: org hierarchy, partners and their effective-dated assignments.
--
-- Dated rows: valid_from and valid_to are both INCLUSIVE; valid_to NULL = still current.
-- Overlaps are impossible: each table has an exclusion constraint on daterange(valid_from, valid_to, '[]').

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE region (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_region_name ON region (lower(name));

CREATE TABLE branch (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_branch_name ON branch (lower(name));

CREATE TABLE employee (
    id         BIGSERIAL PRIMARY KEY,
    emp_code   VARCHAR(30)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('RM', 'HOUSE')),
    status     VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'EXITED')),
    joined_on  DATE,
    exited_on  DATE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (exited_on IS NULL OR joined_on IS NULL OR exited_on >= joined_on),
    CHECK ((status = 'EXITED') = (exited_on IS NOT NULL))
);
CREATE UNIQUE INDEX ux_employee_code ON employee (lower(emp_code));

-- Which region a branch belongs to, over time.
CREATE TABLE branch_region_assignment (
    id         BIGSERIAL PRIMARY KEY,
    branch_id  BIGINT      NOT NULL REFERENCES branch (id),
    region_id  BIGINT      NOT NULL REFERENCES region (id),
    valid_from DATE        NOT NULL,
    valid_to   DATE,
    reason     VARCHAR(500),
    changed_by VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (branch_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_bra_region ON branch_region_assignment (region_id);

-- Region -> RD is 1:1 at any date: one RD per region, one region per RD.
CREATE TABLE rd_assignment (
    id          BIGSERIAL PRIMARY KEY,
    region_id   BIGINT      NOT NULL REFERENCES region (id),
    employee_id BIGINT      NOT NULL REFERENCES employee (id),
    valid_from  DATE        NOT NULL,
    valid_to    DATE,
    reason      VARCHAR(500),
    changed_by  VARCHAR(50) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (region_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&),
    EXCLUDE USING gist (employee_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);

CREATE TABLE rm_branch_assignment (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT      NOT NULL REFERENCES employee (id),
    branch_id   BIGINT      NOT NULL REFERENCES branch (id),
    valid_from  DATE        NOT NULL,
    valid_to    DATE,
    reason      VARCHAR(500),
    changed_by  VARCHAR(50) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (employee_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_rba_branch ON rm_branch_assignment (branch_id);

CREATE TABLE partner (
    id               BIGSERIAL PRIMARY KEY,
    type             VARCHAR(20)  NOT NULL CHECK (type IN ('SUB_BROKER', 'REFERRAL', 'POSP')),
    name             VARCHAR(200) NOT NULL,
    pan_encrypted    TEXT,          -- AES-GCM, see PanCipher
    pan_hash         VARCHAR(64),   -- keyed HMAC for search / duplicate checks
    gstin            VARCHAR(15),
    arn_or_posp_code VARCHAR(50),
    bank_ref         VARCHAR(200),
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((pan_encrypted IS NULL) = (pan_hash IS NULL))
);
CREATE UNIQUE INDEX ux_partner_type_name ON partner (type, lower(name));
CREATE INDEX ix_partner_pan_hash ON partner (pan_hash);

CREATE TABLE partner_rm_link (
    id         BIGSERIAL PRIMARY KEY,
    partner_id BIGINT      NOT NULL REFERENCES partner (id),
    rm_id      BIGINT      NOT NULL REFERENCES employee (id),
    valid_from DATE        NOT NULL,
    valid_to   DATE,
    reason     VARCHAR(500),
    changed_by VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (partner_id WITH =, rm_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_prl_rm ON partner_rm_link (rm_id);

ALTER TABLE app_user ADD CONSTRAINT fk_app_user_employee FOREIGN KEY (employee_id) REFERENCES employee (id);
