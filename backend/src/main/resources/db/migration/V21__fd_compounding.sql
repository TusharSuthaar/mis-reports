-- How a cumulative FD compounds (per FD: companies differ). The maturity value is now worked out on save (R-72).
ALTER TABLE fd_entry ADD COLUMN compounding VARCHAR(20);
