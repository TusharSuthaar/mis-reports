-- One person may be RD of several regions at once (e.g. HO SALES and BHOPAL LLP). A region still has one RD at a time.
DO $$
DECLARE c text;
BEGIN
    SELECT conname INTO c FROM pg_constraint
     WHERE conrelid = 'rd_assignment'::regclass AND contype = 'x' AND pg_get_constraintdef(oid) LIKE '%employee_id WITH =%';
    IF c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE rd_assignment DROP CONSTRAINT %I', c);
    END IF;
END $$;
