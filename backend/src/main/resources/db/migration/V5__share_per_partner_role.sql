-- A client can have a sub-broker AND a referral partner at the same time (each with its own %, 0 allowed).
-- So "one share at a time" becomes "one share per partner role at a time".

ALTER TABLE client_partner_share ADD COLUMN partner_role VARCHAR(20);
UPDATE client_partner_share s SET partner_role = p.type FROM partner p WHERE p.id = s.partner_id;
ALTER TABLE client_partner_share ALTER COLUMN partner_role SET NOT NULL;
ALTER TABLE client_partner_share ADD CONSTRAINT ck_cps_role CHECK (partner_role IN ('SUB_BROKER', 'REFERRAL', 'POSP'));

DO $$
DECLARE c TEXT;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'client_partner_share'::regclass AND contype = 'x'
    LOOP
        EXECUTE format('ALTER TABLE client_partner_share DROP CONSTRAINT %I', c);
    END LOOP;
END $$;

ALTER TABLE client_partner_share ADD CONSTRAINT ex_cps_one_per_role EXCLUDE USING gist (
    client_id WITH =, product_type WITH =, partner_role WITH =, daterange(valid_from, valid_to, '[]') WITH &&);
