-- An entry may keep the figures from the register exactly as they are (R-79): gross, TDS, GST and invoice are stored
-- as given instead of being worked out, so the END file shows what was actually invoiced.
ALTER TABLE fd_entry ADD COLUMN figures_as_given BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bond_entry ADD COLUMN figures_as_given BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE aif_entry ADD COLUMN figures_as_given BOOLEAN NOT NULL DEFAULT false;
