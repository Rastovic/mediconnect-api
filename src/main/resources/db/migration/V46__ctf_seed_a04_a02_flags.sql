-- V46__ctf_seed_a04_a02_flags.sql
-- A04 crypto + A02 misconfig CTF flags. Idempotent.
--   #20  a04-md5-unsalted-passwords          : crack seeded 'crackme' user (MD5 of "sunshine"), log in.
--   #153 a04-predictable-rng-on-reset        : reset 'rngvictim', reproduce PredictablePasswordGen.forUser(id), log in.
--   #28  a02-blanket-securityconfig-weakening : cross-origin state-changing request accepted (CorsProbeFilter).
--   #32  a02-nooppasswordencoder             : submit a user's leaked password_hash as the password (plaintext compare).
-- All awarded via GET /api/ctf/behavior/{slug}.

-- #20 target: password "sunshine" stored as unsalted MD5.
INSERT INTO users (username, email, password_hash, role, active, created_at)
SELECT 'crackme', 'crackme@mediconnect.com', '0571749e2ac330a7455809c6b0e7af90', 'PATIENT', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'crackme');

-- #153 target: password reset to the predictable-RNG value on demand.
INSERT INTO users (username, email, password_hash, role, active, created_at)
SELECT 'rngvictim', 'rngvictim@mediconnect.com', 'd41d8cd98f00b204e9800998ecf8427e', 'PATIENT', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'rngvictim');

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a04-md5-unsalted-passwords','flag{a04_md5_unsalted_passwords_7d3851}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a04-md5-unsalted-passwords');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a04-predictable-rng-on-reset','flag{a04_predictable_rng_on_reset_37c8a1}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a04-predictable-rng-on-reset');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a02-blanket-securityconfig-weakening','flag{a02_blanket_securityconfig_weakening_1b60fd}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a02-blanket-securityconfig-weakening');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a02-nooppasswordencoder','flag{a02_nooppasswordencoder_8422c0}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a02-nooppasswordencoder');
