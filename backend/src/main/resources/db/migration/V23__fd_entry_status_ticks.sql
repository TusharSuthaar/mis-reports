-- A tick on the FD form only (not in the Excel register): the FD has been entered on the Epsilon portal.
ALTER TABLE fd_entry ADD COLUMN on_portal BOOLEAN NOT NULL DEFAULT false;
