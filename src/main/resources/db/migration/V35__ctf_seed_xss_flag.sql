-- V35__ctf_seed_xss_flag.sql
-- CTF flag placement (plan §6.1 A05, finding #55 a05-stored-xss-admin-bot).
--   Exploit: POST /api/messages stores content verbatim; the admin view renders
--            it unescaped. Inject an executable XSS vector, then hit
--            GET /api/ctf/xss/check which detects the unescaped payload
--            server-side (no browser) and returns this flag.
--   Stored in ctf_secret so no plaintext lives in code.
-- Idempotent.

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a05-stored-xss-admin-bot', 'flag{a05_stored_xss_admin_bot_e52c3c}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a05-stored-xss-admin-bot');
