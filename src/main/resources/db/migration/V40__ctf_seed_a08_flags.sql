-- V40__ctf_seed_a08_flags.sql
-- Behavioral CTF flags (plan §12.1), awarded via GET /api/ctf/behavior/{slug}. Idempotent.
--   #210 a08-alg-none-jwt                   : POST /api/doctor/prescriptions/{id}/co-sign {"jwt":"<alg:none token>"}
--   #173 a01-workflow-bypass-force-dispense : POST /api/admin/prescriptions/{id}/force-dispense
--   #177 a08-clinical-value-tamper-no-amend : POST /api/admin/lab-results/{id}/override-value {"resultValue":"..."}

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a08-alg-none-jwt','flag{a08_alg_none_jwt_21cdfe}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a08-alg-none-jwt');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a01-workflow-bypass-force-dispense','flag{a01_workflow_bypass_force_dispense_a6fe2f}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a01-workflow-bypass-force-dispense');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a08-clinical-value-tamper-no-amend','flag{a08_clinical_value_tamper_no_amend_3e8ba3}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a08-clinical-value-tamper-no-amend');
