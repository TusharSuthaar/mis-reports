-- Everything else the Ops client master (Finesse ClientMasterReport) has for a client code, kept so the client's page can
-- show it (R-08): the filled columns of the client sheet, and the rows of its "Client Address Master" sheet. Encrypted
-- like PANs, because they hold phone numbers, e-mails, dates of birth and joint holders' PANs.
ALTER TABLE finesse_client ADD COLUMN details_encrypted TEXT;
ALTER TABLE finesse_client ADD COLUMN addresses_encrypted TEXT;
