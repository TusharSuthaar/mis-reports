-- Indexes for the look-ups the app does all the time, so screens stay fast as the data grows (R-90). Indexes only: no data
-- or behaviour changes.

-- "Contains" search (client name / code typed in a search box: LIKE '%text%') cannot use an ordinary index; trigram
-- indexes can.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_client_name_trgm ON client USING gin (lower(name) gin_trgm_ops);
CREATE INDEX ix_client_external_code_trgm ON client_external_id USING gin (lower(code) gin_trgm_ops);
CREATE INDEX ix_client_external_client_source ON client_external_id (client_id, source);

-- A client's RM now (valid_to IS NULL) and its RM history newest first.
CREATE INDEX ix_cra_current ON client_rm_assignment (client_id) WHERE valid_to IS NULL;
CREATE INDEX ix_cra_client_from ON client_rm_assignment (client_id, valid_from DESC);

-- A client's partners for a product and day; a partner's % for a product and day.
CREATE INDEX ix_cps_client_product ON client_partner_share (client_id, product_type, valid_from);
CREATE INDEX ix_pps_partner_product ON partner_product_share (partner_id, product_type, valid_from);

-- A month's active lines of a product (processing, the month screens), and a client's lines (the client page).
CREATE INDEX ix_line_period_product ON brokerage_line (period_id, product_type) WHERE NOT voided;
CREATE INDEX ix_line_client_product ON brokerage_line (client_id, product_type) WHERE NOT voided;
CREATE INDEX ix_split_line_active ON brokerage_split (brokerage_line_id) WHERE NOT voided;

-- The org on a day: who is in which branch / region and who is RD (history rows open on that day).
CREATE INDEX ix_rba_open ON rm_branch_assignment (employee_id) WHERE valid_to IS NULL;
CREATE INDEX ix_rd_region_from ON rd_assignment (region_id, valid_from DESC);
