-- Branch rule (R-57): a partner for every client of a branch, over time. E.g. one sub-broker for all
-- clients of a branch: it is paid first (its rate card), then the client's own partners share what is left.

CREATE TABLE branch_partner (
    id         BIGSERIAL PRIMARY KEY,
    branch_id  BIGINT       NOT NULL REFERENCES branch (id),
    partner_id BIGINT       NOT NULL REFERENCES partner (id),
    valid_from DATE         NOT NULL,
    valid_to   DATE,
    reason     VARCHAR(500),
    changed_by VARCHAR(50)  NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ex_branch_partner_one_at_a_time EXCLUDE USING gist (
        branch_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_branch_partner_partner ON branch_partner (partner_id);
