-- V37__ctf_seed_behavioral_flags2.sql
-- More behavioral CTF flags (plan §12.1). Each vulnerable endpoint calls
-- CtfBehaviorRegistry.mark(slug) on its illegal-success path; the flag is
-- awarded by GET /api/ctf/behavior/{slug}. Stored in ctf_secret. Idempotent.
--
--   #59  a06-missing-state-machine-dispense : POST /api/prescriptions/{id}/dispense
--        twice (second dispense on an already-DISPENSED prescription).
--   #131 a10-toctou-race-double-dispense     : fire concurrent
--        POST /api/refills/{id}/dispense on a READY refill (race past the guard).
--   #187 a06-unbounded-fan-out               : POST /api/admin/broadcast with no
--        roles (sends to every user).
--   #188 a08-overwrite-in-place-redact       : POST /api/admin/broadcast/{id}/redact
--        overwriting a non-empty message in place.

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a06-missing-state-machine-dispense', 'flag{a06_missing_state_machine_dispense_90f714}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a06-missing-state-machine-dispense');

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a10-toctou-race-double-dispense', 'flag{a10_toctou_race_double_dispense_f86aac}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a10-toctou-race-double-dispense');

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a06-unbounded-fan-out', 'flag{a06_unbounded_fan_out_bea554}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a06-unbounded-fan-out');

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a08-overwrite-in-place-redact', 'flag{a08_overwrite_in_place_redact_e04e41}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a08-overwrite-in-place-redact');
