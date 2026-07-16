-- V44__ctf_seed_csv_injection_flag.sql
-- A05 CSV formula-injection flag, awarded via GET /api/ctf/behavior/{slug}. Idempotent.
--   #264 a05-csv-formula-injection : store a =/+/-/@-prefixed note on an appointment, then GET /api/appointments/export.csv.

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a05-csv-formula-injection','flag{a05_csv_formula_injection_d71149}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a05-csv-formula-injection');
