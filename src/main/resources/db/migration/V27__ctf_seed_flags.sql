-- V27__ctf_seed_flags.sql
-- CTF flag placement (plan §2 Phase 2). Flags are embedded where a real secret
-- would live so capture feels like genuine data exfiltration (plan §11b.3).
-- Idempotent (INSERT ... WHERE NOT EXISTS) so the DB re-seed control (plan §12.4)
-- can rerun it to restore a clean state.
--
-- Placement so far (see CTF_FLAG_PLACEMENT.md for the full 53-flag map):
--   A01 function-level-access / returns-all (finding #93):
--     flag sits in a medical_records.diagnosis row. Reached black-box via
--     GET /api/medical-records (no role check, returns every row) or the IDOR
--     GET /api/medical-records/{id}. The intended exploit is the capture.

INSERT INTO medical_records (patient_id, doctor_id, diagnosis, prescription, created_at, ai_verified)
SELECT 1, 1,
       'RESTRICTED - internal billing reconciliation note. Do not expose to patient portal. ref=flag{a01_function_level_access_returns_all_b9d383}',
       'n/a',
       NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM medical_records WHERE diagnosis LIKE '%flag{a01_function_level_access_returns_all_%'
);
