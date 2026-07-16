-- V28__ctf_seed_idor_appointment.sql
-- CTF flag placement (plan §6.1 A01, finding #42).
--   Challenge: a01-idor-read-any-object-by-id
--   Exploit:   GET /api/appointments/patient/{patientId} has no ownership check.
--              The Appointments page lists the logged-in patient's appointments
--              by patient id; switching patientId in the request returns another
--              patient's appointments. (GET /api/appointments/{id} is the same
--              IDOR at the single-object level.)
--   Placement: flag lives in the notes of an appointment that belongs to a
--              DIFFERENT patient (patient_id 2, not patient1's patient_id 1), so
--              it is reachable only by switching the id, not from the player's own list.
-- Idempotent so the DB re-seed control (plan §12.4) can rerun it.

INSERT INTO appointments (patient_id, doctor_id, status, requested_date, notes, created_at)
SELECT 2, 1, 'APPROVED', NOW(),
       'PRIVATE consult note (do not share with patient portal). internal-ref=flag{a01_idor_read_any_object_by_id_19ab47}',
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM appointments WHERE notes LIKE '%flag{a01_idor_read_any_object_by_id_%'
);
