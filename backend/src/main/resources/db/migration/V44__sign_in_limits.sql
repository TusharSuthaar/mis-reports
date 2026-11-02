-- Sign-in limits (H-02, R-101): wrong passwords per user (a growing wait after 5, locked after 10 until an admin unlocks),
-- and failures per address (a pause after 20 in 15 minutes, whoever the usernames were).
ALTER TABLE app_user ADD COLUMN failed_logins INT         NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until  TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN locked        BOOLEAN     NOT NULL DEFAULT FALSE;

CREATE TABLE login_failure (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    ip       VARCHAR(64)  NOT NULL,
    at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_failure_ip ON login_failure (ip, at);
