-- Phase 2: clients, their RM over time, and how source-file spellings map to our RMs / partners.

CREATE TABLE client (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(300) NOT NULL,
    pan_encrypted         TEXT,          -- AES-GCM, see PanCipher
    pan_hash              VARCHAR(64),   -- keyed HMAC for search / duplicate checks
    family_head_code      VARCHAR(50),   -- family head's code in the source system, as given
    family_head_client_id BIGINT REFERENCES client (id), -- resolved once that client exists
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((pan_encrypted IS NULL) = (pan_hash IS NULL))
);
CREATE INDEX ix_client_pan_hash ON client (pan_hash);
CREATE INDEX ix_client_name ON client (lower(name));

-- A client's code in an outside system (Investwell CLIENT IWELL CODE + CODE 2). The join key for income files.
CREATE TABLE client_external_id (
    id        BIGSERIAL PRIMARY KEY,
    client_id BIGINT      NOT NULL REFERENCES client (id),
    source    VARCHAR(20) NOT NULL,
    code      VARCHAR(50) NOT NULL,
    code2     VARCHAR(50),
    UNIQUE (source, code)
);
CREATE INDEX ix_client_external_client ON client_external_id (client_id);

-- Who the client's RM is, over time. Inclusive dates; valid_to NULL = current. Never overlapping.
CREATE TABLE client_rm_assignment (
    id         BIGSERIAL PRIMARY KEY,
    client_id  BIGINT      NOT NULL REFERENCES client (id),
    rm_id      BIGINT      NOT NULL REFERENCES employee (id),
    valid_from DATE        NOT NULL,
    valid_to   DATE,
    reason     VARCHAR(500),
    changed_by VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    EXCLUDE USING gist (client_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
);
CREATE INDEX ix_cra_rm ON client_rm_assignment (rm_id);

-- "This spelling in this source means that RM / partner". Matched after upper-casing and collapsing spaces.
CREATE TABLE source_alias (
    id                   BIGSERIAL PRIMARY KEY,
    source               VARCHAR(20)  NOT NULL,
    entity_type          VARCHAR(10)  NOT NULL CHECK (entity_type IN ('RM', 'PARTNER')),
    raw_value_normalized VARCHAR(300) NOT NULL,
    target_id            BIGINT       NOT NULL,
    created_by           VARCHAR(50)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (source, entity_type, raw_value_normalized)
);
