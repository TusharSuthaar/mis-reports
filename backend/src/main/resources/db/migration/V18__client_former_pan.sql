-- A PAN corrected on the Clients screen is remembered for its client: later files (Master MIS, Finesse) that still
-- carry the old, wrong PAN then find that client instead of adding a new one. Hash only (the PAN itself is not kept).
CREATE TABLE client_former_pan (
    pan_hash   VARCHAR(64) PRIMARY KEY,
    client_id  BIGINT      NOT NULL REFERENCES client (id),
    changed_by VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_client_former_pan_client ON client_former_pan (client_id);
