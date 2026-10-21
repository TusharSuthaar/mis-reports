-- Exchange rates by day (R-84): fetched from Frankfurter (European Central Bank rates) the first time a day is needed,
-- or set by hand. rate_date is the day the rate is really from (the last ECB working day on or before "day").
CREATE TABLE exchange_rate (
    currency   VARCHAR(3)     NOT NULL,
    day        DATE           NOT NULL,
    rate       NUMERIC(12, 4) NOT NULL CHECK (rate > 0),
    rate_date  DATE           NOT NULL,
    source     VARCHAR(20)    NOT NULL CHECK (source IN ('ECB', 'MANUAL')),
    reason     VARCHAR(500),
    changed_by VARCHAR(50)    NOT NULL,
    changed_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (currency, day)
);

-- A GIFT City entry remembers the day of the rate it used and where the rate came from.
ALTER TABLE gift_entry ADD COLUMN fx_rate_date DATE;
ALTER TABLE gift_entry ADD COLUMN fx_source VARCHAR(80);
