-- Phase C: every job remembers the request that started it, and unexpected errors are kept (by reference) so
-- "it failed, reference 7K2MQX4P" can be looked up on the Activity screen and in the logs.

ALTER TABLE job ADD COLUMN request_id VARCHAR(40);
CREATE INDEX ix_job_created_at ON job (created_at);

CREATE TABLE error_log (
    id         BIGSERIAL PRIMARY KEY,
    reference  VARCHAR(40)  NOT NULL,
    at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    by_user    VARCHAR(50)  NOT NULL,
    method     VARCHAR(10),
    path       VARCHAR(300),
    status     INT          NOT NULL,
    message    TEXT         NOT NULL   -- PANs masked before saving; no stack traces (those are in the logs)
);
CREATE INDEX ix_error_log_at ON error_log (at);
CREATE INDEX ix_error_log_reference ON error_log (reference);
