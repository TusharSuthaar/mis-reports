-- Finesse replaces Investwell (from August 2026). The brokerage file carries only Finesse client codes; the Finesse
-- client master export says which PAN (or, for a minor, which guardian's PAN) each code belongs to. Clients are the
-- Master MIS entries, one per PAN: several codes of one person, and a minor's code, all land on that client.

CREATE TABLE finesse_client (
    code                   VARCHAR(20)  PRIMARY KEY,
    name                   VARCHAR(300) NOT NULL,
    family_head_code       VARCHAR(20),
    pan_encrypted          TEXT,
    pan_hash               VARCHAR(64),
    guardian_pan_encrypted TEXT,
    guardian_pan_hash      VARCHAR(64),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             VARCHAR(50)  NOT NULL,
    CHECK ((pan_encrypted IS NULL) = (pan_hash IS NULL)),
    CHECK ((guardian_pan_encrypted IS NULL) = (guardian_pan_hash IS NULL))
);
CREATE INDEX ix_finesse_client_pan ON finesse_client (pan_hash);
CREATE INDEX ix_finesse_client_guardian ON finesse_client (guardian_pan_hash);

-- How each brokerage row found its client: PAN, GUARDIAN_PAN, or NOT_IN_MASTER (with the reason in match_note).
ALTER TABLE staged_row ADD COLUMN match_kind VARCHAR(20);
ALTER TABLE staged_row ADD COLUMN match_note VARCHAR(200);
