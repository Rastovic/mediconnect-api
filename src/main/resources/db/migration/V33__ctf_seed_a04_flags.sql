-- V33__ctf_seed_a04_flags.sql
-- CTF flag placement (plan §6.1 A04). Idempotent.
--
--   A04 #7  a04-plaintext-pii-at-rest:
--     insurance_number is plaintext and exposed unmasked in PatientDto. Flag is
--     stored in patient 2's insurance_number (user_id 9). Capture by reading
--     another patient: GET /api/patients/by-user/9.
--
--   A04 #16 a04-hardcoded-jwt-signing-secret:
--     JWT secret is hardcoded (mediconnect-super-secret-2024). Forge a token for
--     subject "admin" and call GET /api/ctf/admin-secret, which returns this flag
--     only to an ADMIN identity. Stored in ctf_secret so no plaintext lives in code.

-- A04 #7: plaintext PII flag
UPDATE patients
SET insurance_number = 'INS-000-flag{a04_plaintext_pii_at_rest_106dfd}'
WHERE id = 2 AND insurance_number NOT LIKE '%flag{a04_plaintext_pii_at_rest_%';

-- A04 #16: admin-gated flag, read by GET /api/ctf/admin-secret
INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a04-hardcoded-jwt-signing-secret', 'flag{a04_hardcoded_jwt_signing_secret_313077}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a04-hardcoded-jwt-signing-secret');
