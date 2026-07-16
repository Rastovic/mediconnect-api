-- V34__ctf_seed_ssrf_flag.sql
-- CTF flag placement (plan §6.1 A10, finding #220 a10-ssrf-server-fetches-your-url).
--   Exploit (reflected SSRF): POST /api/doctor/lab-orders with
--     {"customQueryUrl":"http://127.0.0.1:8099/internal/metadata"}
--   The server fetches the caller URL (no allow-list) and returns the body in
--   catalogueResponse. The internal endpoint lives on an unpublished connector
--   (port 8099), so it is reachable only server-side, not from the host browser.
--   Flag is served as internal_token; stored here so no plaintext lives in code.
-- Idempotent.

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a10-ssrf-server-fetches-your-url', 'flag{a10_ssrf_server_fetches_your_url_c748b1}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a10-ssrf-server-fetches-your-url');
