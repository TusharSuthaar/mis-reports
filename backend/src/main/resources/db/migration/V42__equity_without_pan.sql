-- The broker's Brokerage Due report also comes without a PAN column (Sep 2026 layout): a trade is then matched by its party
-- code (the PAN that code had in earlier uploads) or by the client's exact name (R-89). A trade that neither finds is kept
-- without a PAN and blocks processing until the client is added.
ALTER TABLE equity_trade ALTER COLUMN pan_encrypted DROP NOT NULL;
ALTER TABLE equity_trade ALTER COLUMN pan_hash DROP NOT NULL;
CREATE INDEX ix_equity_trade_party ON equity_trade (party_code);
