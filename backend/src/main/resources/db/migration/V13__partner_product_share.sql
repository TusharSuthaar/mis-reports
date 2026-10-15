-- Product-wise partner sharing (rule R-53..R-56): each partner has a % per product, over time. A client's link to a
-- partner may carry its own % (an exception); without one, the partner's % for the product applies.

-- Products a share can be for (Master MIS partner sheets: MF, Pre-IPO, FD, Bonds, Loans, Equity, PMS, AIF, Advisory,
-- GIFT, Insurance).
ALTER TABLE client_partner_share DROP CONSTRAINT IF EXISTS client_partner_share_product_type_check;
ALTER TABLE client_partner_share ADD CONSTRAINT ck_cps_product CHECK (product_type IN
    ('MF', 'PREIPO', 'FD', 'BOND', 'LOAN', 'EQUITY', 'PMS', 'AIF', 'ADVISORY', 'GIFT', 'INS'));

-- NULL = no exception: the partner's % for the product applies.
ALTER TABLE client_partner_share ALTER COLUMN share DROP NOT NULL;

CREATE TABLE partner_product_share (
    id           BIGSERIAL PRIMARY KEY,
    partner_id   BIGINT        NOT NULL REFERENCES partner (id),
    product_type VARCHAR(10)   NOT NULL CHECK (product_type IN
        ('MF', 'PREIPO', 'FD', 'BOND', 'LOAN', 'EQUITY', 'PMS', 'AIF', 'ADVISORY', 'GIFT', 'INS')),
    share        NUMERIC(9, 6) NOT NULL CHECK (share >= 0 AND share <= 1),  -- fraction of net: 0.8 = 80%
    valid_from   DATE          NOT NULL,
    valid_to     DATE,
    reason       VARCHAR(500),
    changed_by   VARCHAR(50)   NOT NULL,
    changed_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ex_pps_one_at_a_time EXCLUDE USING gist (
        partner_id WITH =, product_type WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_pps_partner ON partner_product_share (partner_id);

-- "Under process" partners (approval pending): linked to clients but paid nothing until ACTIVE.
ALTER TABLE partner DROP CONSTRAINT IF EXISTS partner_status_check;
ALTER TABLE partner ADD CONSTRAINT ck_partner_status CHECK (status IN ('ACTIVE', 'PENDING', 'INACTIVE'));
