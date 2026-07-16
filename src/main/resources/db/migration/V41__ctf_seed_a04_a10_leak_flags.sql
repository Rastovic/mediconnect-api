-- V41__ctf_seed_a04_a10_leak_flags.sql
-- Data-leak CTF flags positioned as readable field values. Idempotent.
--   #11  a04-passwordhash-exposed-in-responses : GET /api/users leaks password_hash of every user;
--        a dedicated target account carries the flag as its hash (harvest it straight from the API).
--   #136 a10-verbose-exception-leakage         : GET /api/refills returns raw failure_reason text;
--        a seeded FAILED refill leaks an internal path + the flag in its exception message.

-- #11: target user whose exposed password_hash IS the flag.
INSERT INTO users (username, email, password_hash, role, active, created_at)
SELECT 'records_svc', 'records_svc@mediconnect.com',
       'flag{a04_passwordhash_exposed_in_responses_b703d4}', 'PATIENT', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'records_svc');

-- #136: FAILED refill whose verbose failure_reason leaks the flag.
INSERT INTO refill_requests
    (prescription_id, patient_id, requested_by, quantity, status, failure_reason, retry_count, created_at)
SELECT 1, 1, 1, 30, 'FAILED',
       'java.io.IOException: refill batch aborted at /opt/mediconnect/secure/refill.keystore -- internal diagnostic token flag{a10_verbose_exception_leakage_90058f}',
       1, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM refill_requests
    WHERE failure_reason LIKE '%a10_verbose_exception_leakage%'
);
