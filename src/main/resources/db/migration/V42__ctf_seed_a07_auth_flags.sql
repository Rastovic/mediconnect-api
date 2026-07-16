-- V42__ctf_seed_a07_auth_flags.sql
-- A07 Authentication-Failures CTF flags, awarded via GET /api/ctf/behavior/{slug}. Idempotent.
--   #25 a07-user-enumeration        : login with a valid username + wrong password (distinct-message oracle).
--   #27 a07-no-login-rate-limiting  : >=5 failed logins for one account, no throttle/lockout.
--   #33 a07-expiry-skip-path-bypass : present an EXPIRED token to /api/public|legacy|reports/**.
--   #34 a07-fail-open-token-validation : present a malformed/tampered token; parse error swallowed.

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a07-user-enumeration','flag{a07_user_enumeration_97360a}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a07-user-enumeration');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a07-no-login-rate-limiting','flag{a07_no_login_rate_limiting_cc1b17}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a07-no-login-rate-limiting');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a07-expiry-skip-path-bypass','flag{a07_expiry_skip_path_bypass_957f29}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a07-expiry-skip-path-bypass');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a07-fail-open-token-validation','flag{a07_fail_open_token_validation_bdba1f}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a07-fail-open-token-validation');
