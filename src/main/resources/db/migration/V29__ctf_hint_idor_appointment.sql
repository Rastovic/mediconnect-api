-- V29__ctf_hint_idor_appointment.sql
-- Update the hint for the A01 appointment IDOR (finding #42). The exploit is
-- pure API tampering (the UI only ever shows your own list), so the hint is the
-- breadcrumb that points a student at the switchable numeric patient id.
-- Idempotent (UPDATE), so the DB re-seed control can rerun it.

UPDATE ctf_challenge
SET target_hint = 'Open the network tab while the Appointments page loads. It fetches your list via GET /api/appointments/patient/{yourPatientId} - a numeric id. Nothing checks that the id belongs to you. Replay the request with a different patient id (2, 3, ...) and read someone else''s appointments.'
WHERE slug = 'a01-idor-read-any-object-by-id';
