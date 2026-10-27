-- Insurance (R-94, R-95): one row per policy, as in the insurance team's "Policy level data" sheet. The client is found by PAN
-- (RM from the client master, never typed); the POSP is a partner of type POSP. Expected commission = net premium ×
-- expected %; the POSP's commission = net premium × expected % × agreed sharing (either may be typed as an amount instead).
-- Each active policy is an Insurance line of its business month: expected commission in the END's Insurance column, the
-- POSP's commission as POSP Sharing.
CREATE TABLE insurance_policy (
    id                BIGSERIAL PRIMARY KEY,
    month             DATE           NOT NULL CHECK (extract(day FROM month) = 1),
    client_id         BIGINT         NOT NULL,
    rm_id             BIGINT         NOT NULL,
    posp_id           BIGINT,
    insurer           VARCHAR(200)   NOT NULL,
    category          VARCHAR(20)    NOT NULL CHECK (category IN ('LIFE', 'HEALTH', 'MOTOR', 'GENERAL')),
    sub_type          VARCHAR(100),
    product_name      VARCHAR(300)   NOT NULL,
    policy_no         VARCHAR(100)   NOT NULL,
    business_type     VARCHAR(20)    NOT NULL CHECK (business_type IN ('NEW', 'RENEWAL', 'PORT', 'ENDORSEMENT', 'FYCI')),
    ppt               VARCHAR(40),
    policy_term       INTEGER,
    sum_assured       NUMERIC(18, 2),
    od_premium        NUMERIC(18, 2),
    tp_premium        NUMERIC(18, 2),
    total_premium     NUMERIC(18, 2) NOT NULL,
    net_premium       NUMERIC(18, 2) NOT NULL,
    expected_percent  NUMERIC(9, 6),
    expected_comm     NUMERIC(18, 2) NOT NULL,
    expected_as_given BOOLEAN        NOT NULL DEFAULT false,
    actual_percent    NUMERIC(9, 6),
    actual_comm       NUMERIC(18, 2),
    posp_share        NUMERIC(9, 6),
    posp_percent      NUMERIC(9, 6),
    posp_comm         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    posp_as_given     BOOLEAN        NOT NULL DEFAULT false,
    remarks           VARCHAR(1000),
    source            VARCHAR(20)    NOT NULL DEFAULT 'SCREEN',
    upload_row        INTEGER,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_by        VARCHAR(50)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by        VARCHAR(50)    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_insurance_policy_month ON insurance_policy (month);
CREATE INDEX ix_insurance_policy_client ON insurance_policy (client_id);
CREATE INDEX ix_insurance_policy_no ON insurance_policy (lower(policy_no));

-- Ledger: a line may come from an insurance policy.
ALTER TABLE brokerage_line ADD COLUMN insurance_policy_id BIGINT;
ALTER TABLE brokerage_line DROP CONSTRAINT brokerage_line_source_check;
ALTER TABLE brokerage_line ADD CONSTRAINT brokerage_line_source_check
    CHECK (upload_id IS NOT NULL OR fd_entry_id IS NOT NULL OR bond_entry_id IS NOT NULL OR aif_entry_id IS NOT NULL
        OR loan_entry_id IS NOT NULL OR gift_entry_id IS NOT NULL OR equity_upload_id IS NOT NULL OR preipo_entry_id IS NOT NULL
        OR pms_fee_id IS NOT NULL OR nps_entry_id IS NOT NULL OR p2p_entry_id IS NOT NULL OR insurance_policy_id IS NOT NULL);
DROP INDEX ux_brokerage_line_active;
CREATE UNIQUE INDEX ux_brokerage_line_active ON brokerage_line (period_id, client_id, product_type, coalesce(adjusts_period_id, 0),
                                                               coalesce(fd_entry_id, 0), coalesce(bond_entry_id, 0),
                                                               coalesce(aif_entry_id, 0), coalesce(loan_entry_id, 0),
                                                               coalesce(gift_entry_id, 0), coalesce(preipo_entry_id, 0),
                                                               coalesce(pms_fee_id, 0), coalesce(nps_entry_id, 0),
                                                               coalesce(p2p_entry_id, 0), coalesce(insurance_policy_id, 0)) WHERE NOT voided;

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
       l.p2p_entry_id,
       l.insurance_policy_id
FROM brokerage_line l
JOIN brokerage_split s ON s.brokerage_line_id = l.id AND NOT s.voided
JOIN client c ON c.id = l.client_id
WHERE NOT l.voided
GROUP BY l.id, c.id;
