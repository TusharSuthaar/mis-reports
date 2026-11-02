-- A client found only by its name (no PAN) is a suggestion until someone confirms it (R-100). The confirmation is
-- remembered as a CLIENT alias: in that source, this code / party code / name is that client, so it is asked once.
ALTER TABLE source_alias DROP CONSTRAINT source_alias_entity_type_check;
ALTER TABLE source_alias ADD CONSTRAINT source_alias_entity_type_check CHECK (entity_type IN ('RM', 'PARTNER', 'CLIENT'));

-- How each equity trade found its client. Only PAN, PARTY (the party code's PAN in the report or a confirmed earlier
-- trade) and CONFIRMED pass a PAN on to later uploads; a NAME match does not until it is confirmed. Trades already
-- saved keep PAN: how they were found was not recorded.
ALTER TABLE equity_trade ADD COLUMN matched_by VARCHAR(10) NOT NULL DEFAULT 'PAN'
    CHECK (matched_by IN ('PAN', 'PARTY', 'NAME', 'CONFIRMED'));

-- A policy whose sheet row had no PAN and went to the one client with that name: waits for a confirmation.
ALTER TABLE insurance_policy ADD COLUMN matched_by_name BOOLEAN NOT NULL DEFAULT FALSE;
