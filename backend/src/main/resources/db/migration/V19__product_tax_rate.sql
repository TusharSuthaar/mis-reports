-- GST and TDS per product, dated: a change applies from a date, earlier months keep the old rate (R-71).
CREATE TABLE product_tax_rate (
    id             BIGSERIAL PRIMARY KEY,
    product_type   VARCHAR(10)   NOT NULL,
    gst_percent    NUMERIC(6, 3) NOT NULL CHECK (gst_percent >= 0 AND gst_percent <= 100),
    tds_percent    NUMERIC(6, 3) NOT NULL CHECK (tds_percent >= 0 AND tds_percent <= 100),
    tds_by_default BOOLEAN       NOT NULL DEFAULT false,
    valid_from     DATE          NOT NULL,
    valid_to       DATE,
    reason         VARCHAR(500),
    changed_by     VARCHAR(50)   NOT NULL,
    changed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (product_type WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);

-- Known today: GST 18% on MF and FD from go-live. TDS is set on the Products & taxes screen.
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, reason, changed_by)
VALUES ('MF', 18, 0, false, '2026-08-01', 'Starting rate', 'system'),
       ('FD', 18, 0, false, '2026-08-01', 'Starting rate', 'system');
