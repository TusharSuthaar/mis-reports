-- Regions are listed (Org screen, END region section) in a chosen order; a region can show its branches as rows above
-- it in the END region section (e.g. a region with two big branches).
ALTER TABLE region ADD COLUMN position INT;
ALTER TABLE region ADD COLUMN end_branch_rows BOOLEAN NOT NULL DEFAULT false;
UPDATE region SET position = s.n
  FROM (SELECT id, row_number() OVER (ORDER BY lower(name)) AS n FROM region) s
 WHERE region.id = s.id;
ALTER TABLE region ALTER COLUMN position SET NOT NULL;
