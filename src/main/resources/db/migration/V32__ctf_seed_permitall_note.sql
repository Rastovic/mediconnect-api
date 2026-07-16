-- V32__ctf_seed_permitall_note.sql
-- CTF flag placement (plan §6.1 A01, finding #191 a01-permitall-on-protected-routes).
--   Exploit:   the /api/doctor/** controllers are permitAll(). A PATIENT can call
--              GET /api/doctor/notes directly (no DOCTOR role required) and read
--              clinical notes. The flag is inside one note's rendered_html.
--   Capture:   curl -s -H "Authorization: Bearer <patient token>" http://localhost:8085/api/doctor/notes
-- Idempotent so the DB re-seed control can rerun it.

INSERT INTO clinical_notes (patient_id, doctor_id, template_name, raw_data, rendered_html, created_at, updated_at)
SELECT 1, 1, 'Internal Access Review',
       '{"visibility":"doctors-only"}',
       '<p>Restricted clinician note. Internal access key: flag{a01_permitall_on_protected_routes_55de41}</p>',
       NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM clinical_notes WHERE rendered_html LIKE '%flag{a01_permitall_on_protected_routes_%'
);
