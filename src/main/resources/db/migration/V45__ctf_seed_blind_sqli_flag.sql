-- V45__ctf_seed_blind_sqli_flag.sql
-- A05 blind boolean-based SQLi flag. Extracted char-by-char via the row-count oracle
-- on GET /api/doctor/patients?q=... (DoctorRosterService#listPatients). Idempotent.
--   #194 a05-blind-boolean-sqli-multi-clause : boolean-extract ctf_secret.flag one char at a time.

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a05-blind-boolean-sqli-multi-clause','flag{a05_blind_boolean_sqli_multi_clause_69f1ca}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a05-blind-boolean-sqli-multi-clause');
