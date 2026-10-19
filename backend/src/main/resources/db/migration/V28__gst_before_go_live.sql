-- GST was 18% on MF and FD commission before go-live too (since GST began, 1 Jul 2017): history entered from the
-- registers (R-79) takes its month's rate like any other entry (R-71).
INSERT INTO product_tax_rate (product_type, gst_percent, tds_percent, tds_by_default, valid_from, valid_to, reason, changed_by)
VALUES ('MF', 18, 0, false, '2017-07-01', '2026-07-31', 'GST before go-live', 'system'),
       ('FD', 18, 0, false, '2017-07-01', '2026-07-31', 'GST before go-live', 'system');
