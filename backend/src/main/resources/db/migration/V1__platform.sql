-- Phase 0: users, audit log, background jobs.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('RM', 'RD', 'HO', 'FINANCE', 'ADMIN')),
    employee_id   BIGINT,      -- FK to employee is added in Phase 1
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_app_user_username ON app_user (lower(username));

-- Append-only: rows can be inserted, never changed or removed.
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity      VARCHAR(60)  NOT NULL,
    entity_id   VARCHAR(60),
    action      VARCHAR(40)  NOT NULL,
    before_json JSONB,
    after_json  JSONB,
    by_user     VARCHAR(50)  NOT NULL,
    at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_entity ON audit_log (entity, entity_id);
CREATE INDEX ix_audit_log_at ON audit_log (at);

CREATE FUNCTION audit_log_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();

CREATE TABLE job (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(60)  NOT NULL,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    payload     JSONB,
    result      JSONB,
    error       TEXT,
    created_by  VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);
CREATE INDEX ix_job_status ON job (status);
