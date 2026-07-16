-- V36__ctf_seed_behavioral_flags.sql
-- Behavioral CTF flags (plan §12.1). The flag exists only as proof the illegal
-- action succeeded; the vulnerable endpoint calls CtfBehaviorRegistry.mark(slug)
-- and GET /api/ctf/behavior/{slug} returns the flag. Stored in ctf_secret.
--
--   A02 #169 a02-destructive-ops-endpoint-system-exit:
--     POST /api/admin/maintenance/restart reaches the (now defused) System.exit
--     path -> mark. Flag: GET /api/ctf/behavior/a02-destructive-ops-endpoint-system-exit
--   A06 #43  a06-missing-state-machine-appointment:
--     PUT /api/appointments/{id}/status with an impossible transition, e.g. a
--     COMPLETED appointment back to REQUESTED -> mark. Flag:
--     GET /api/ctf/behavior/a06-missing-state-machine-appointment
-- Idempotent.

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a02-destructive-ops-endpoint-system-exit', 'flag{a02_destructive_ops_endpoint_system_exit_9a44af}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a02-destructive-ops-endpoint-system-exit');

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a06-missing-state-machine-appointment', 'flag{a06_missing_state_machine_appointment_bdcdf6}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a06-missing-state-machine-appointment');
