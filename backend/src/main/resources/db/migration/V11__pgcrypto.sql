-- Lets an admin find a client by PAN in psql: PANs are stored encrypted, with pan_hash = hex(HMAC-SHA256(PAN, hash key)).
--   \set hk '<MIS_PAN_HASH_KEY>'
--   SELECT id, name FROM client WHERE pan_hash = encode(hmac(upper('ABCDE1234F'), decode(:'hk', 'base64'), 'sha256'), 'hex');
CREATE EXTENSION IF NOT EXISTS pgcrypto;
