-- FD sheet alignment (2026-10-19): "FD Name" is the FD company; "PARTNER" is the platform the FD is booked through
-- (e.g. J M Finance). The deposit period is kept in months, as in the sheet; the maturity value uses it (R-72).
ALTER TABLE fd_entry RENAME COLUMN issuer TO platform;
ALTER TABLE fd_entry ADD COLUMN period_months INT;
UPDATE fd_entry SET period_months = GREATEST(1, (extract(year FROM age(maturity_date, deposit_date)) * 12
                                                + extract(month FROM age(maturity_date, deposit_date)))::int);
ALTER TABLE fd_entry ALTER COLUMN period_months SET NOT NULL;
ALTER TABLE fd_entry ADD CONSTRAINT fd_entry_period_months_check CHECK (period_months BETWEEN 1 AND 600);
