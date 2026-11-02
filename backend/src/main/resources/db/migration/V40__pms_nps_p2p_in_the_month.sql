-- PMS, NPS and P2P join month processing and the END file (R-85..R-87).
-- PMS revenue is typed per client for the month it is received, against the client's PMS investment (client, RM and
-- partner come from the investment). NPS and P2P lines come from their entries' revenue / brokerage, in the entry's month.
CREATE TABLE pms_fee (
    id            BIGSERIAL PRIMARY KEY,
    investment_id BIGINT         NOT NULL REFERENCES pms_investment (id),
    month         DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    amount        NUMERIC(16, 2) NOT NULL CHECK (amount <> 0),
    remarks       VARCHAR(1000),
    status        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(50)    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by    VARCHAR(50)    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_pms_fee_month ON pms_fee (month);
CREATE INDEX ix_pms_fee_investment ON pms_fee (investment_id);

-- Ledger: a line may come from a PMS fee, an NPS entry or a P2P entry.
ALTER TABLE brokerage_line ADD COLUMN pms_fee_id BIGINT;
ALTER TABLE brokerage_line ADD COLUMN nps_entry_id BIGINT;
ALTER TABLE brokerage_line ADD COLUMN p2p_entry_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL
        OR loan_entry_id IS NOT NULL OR gift_entry_id IS NOT NULL OR equity_upload_id IS NOT NULL OR preipo_entry_id IS NOT NULL
        OR pms_fee_id IS NOT NULL OR nps_entry_id IS NOT NULL OR p2p_entry_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0),
                                                               coalesce(aif_entry_id, 0), coalesce(loan_entry_id, 0),
                                                               coalesce(gift_entry_id, 0), coalesce(preipo_entry_id, 0),
                                                               coalesce(pms_fee_id, 0), coalesce(nps_entry_id, 0),
                                                               coalesce(p2p_entry_id, 0)) WHERE NOT voided;

CREATE OR REPLACE VIEW v_line_split AS
SELECT l.id                                                                   AS line_id,
       l.period_id,
       l.product_type,
       l.client_id,
       c.name                                                                 AS client_name,
       c.pan_encrypted,
       l.gross,
       l.gst,
       l.net,
       l.adjusts_period_id,
       max(s.region_at_period)                                                AS region,
       max(s.rd_at_period)                                                    AS rd,
       max(s.branch_at_period)                                                AS branch,
       max(s.rm_at_period)                                                    AS rm,
       max(s.partner_name) FILTER (WHERE s.partner_role = 'SUB_BROKER')       AS sub_broker,
       max(s.share_pct)    FILTER (WHERE s.partner_role = 'SUB_BROKER')       AS sub_broker_pct,
       coalesce(sum(s.amount) FILTER (WHERE s.partner_role = 'SUB_BROKER'), 0) AS sub_broker_amount,
       max(s.partner_name) FILTER (WHERE s.partner_role = 'REFERRAL')         AS referral,
       max(s.share_pct)    FILTER (WHERE s.partner_role = 'REFERRAL')         AS referral_pct,
       coalesce(sum(s.amount) FILTER (WHERE s.partner_role = 'REFERRAL'), 0)  AS referral_amount,
       max(s.partner_name) FILTER (WHERE s.partner_role = 'POSP')             AS posp,
       coalesce(sum(s.amount) FILTER (WHERE s.partner_role = 'POSP'), 0)      AS posp_amount,
       coalesce(sum(s.amount) FILTER (WHERE s.payee_type = 'COMPANY'), 0)     AS epsilon_amount,
       l.fd_entry_id,
       l.bond_entry_id,
       l.aif_entry_id,
       l.loan_entry_id,
       max(s.rm_id_at_period)                                                 AS rm_id,
       max(s.partner_id) FILTER (WHERE s.partner_role = 'SUB_BROKER')         AS sub_broker_id,
       max(s.partner_id) FILTER (WHERE s.partner_role = 'REFERRAL')           AS referral_id,
       max(s.partner_id) FILTER (WHERE s.partner_role = 'POSP')               AS posp_id,
       l.gift_entry_id,
       l.preipo_entry_id,
       l.pms_fee_id,
       l.nps_entry_id,
       l.p2p_entry_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
