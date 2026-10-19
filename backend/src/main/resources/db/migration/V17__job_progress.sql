-- How far a running job is (step i of n, item done of total), so screens can show a progress bar.
ALTER TABLE job ADD COLUMN progress JSONB;
