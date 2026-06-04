-- [A10] Mishandling of Exceptional Conditions — Async Refill Queue schema
--
-- Intentionally weak schema for educational purposes:
--   - `quantity` is NULLABLE → triggers CWE-754 (improper check) and feeds the
--     fail-open NPE catch in RefillQueueService.processOne.
--   - NO UNIQUE constraint on (prescription_id, status='READY') → enables the
--     CWE-362 double-dispense race condition.
--   - `failure_reason` stores raw exception text (CWE-209 leak surface).
--   - `temp_slip_path` stores absolute filesystem path of an internal temp file
--     (CWE-209 / A09 path disclosure when this row is returned in API responses).
--   - `retry_count` exists but has no max-retry constraint (CWE-400 amplification).
CREATE TABLE refill_requests (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    prescription_id BIGINT       NOT NULL,
    patient_id      BIGINT       NOT NULL,
    -- [A07] caller-supplied actor identifier; never re-checked against the JWT
    requested_by    BIGINT       NULL,
    -- [A10] CWE-754: nullable on purpose so the validator NPE can be triggered
    quantity        INT          NULL,
    -- REQUESTED | VALIDATING | READY | DISPENSED | FAILED
    status          VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    -- [A10][A09] raw exception text; returned verbatim by the list endpoint
    failure_reason  VARCHAR(512) NULL,
    -- [A10] CWE-400 amplification — no max-retry guard
    retry_count     INT          NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispensed_at    TIMESTAMP    NULL,
    pharmacist_id   BIGINT       NULL,
    -- [A10] CWE-460 — absolute path of the temp slip file; leaked through the DTO
    temp_slip_path  VARCHAR(512) NULL,
    CONSTRAINT fk_refill_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions(id),
    CONSTRAINT fk_refill_patient      FOREIGN KEY (patient_id)      REFERENCES patients(id),
    CONSTRAINT fk_refill_pharmacist   FOREIGN KEY (pharmacist_id)   REFERENCES users(id)
    -- [A10] intentionally NO UNIQUE constraint on (prescription_id, status)
    --        → enables CWE-362 double-dispense race
);

-- Seed: a handful of rows in each lifecycle state, including one with NULL quantity
--        so the fail-open NPE is reproducible on first worker tick.
--        Uses SELECT-based lookups (same pattern as V11/V12) so the seed survives
--        any future re-ordering of the user / patient / prescription seeds.

SET @pharmacist_uid    = (SELECT id FROM users    WHERE username = 'pharmacist1');
SET @patient1_uid      = (SELECT id FROM users    WHERE username = 'patient1');
SET @patient1_pid      = (SELECT id FROM patients WHERE user_id  = @patient1_uid);

-- prescription id 1 (Ferrous Sulphate) belongs to patient1 — used by 4 of the
-- 5 seed rows so the relationships are always consistent.
SET @rx_p1             = (SELECT MIN(id) FROM prescriptions WHERE patient_id = @patient1_pid);

INSERT INTO refill_requests
    (prescription_id, patient_id, requested_by, quantity, status, created_at)
VALUES
    -- normal request, will pass validation
    (@rx_p1, @patient1_pid, @patient1_uid, 30,   'REQUESTED', NOW() - INTERVAL 5 MINUTE),
    -- [A10] NULL quantity → validator NPE → fail-open promotes to READY
    (@rx_p1, @patient1_pid, @patient1_uid, NULL, 'REQUESTED', NOW() - INTERVAL 4 MINUTE),
    -- already READY — eligible for the double-dispense demo
    (@rx_p1, @patient1_pid, @patient1_uid, 60,   'READY',     NOW() - INTERVAL 1 HOUR),
    -- historic FAILED entry with a raw exception class in failure_reason (CWE-209)
    (@rx_p1, @patient1_pid, @patient1_uid, 999,  'FAILED',    NOW() - INTERVAL 2 HOUR),
    -- DISPENSED reference row (attributed to pharmacist1)
    (@rx_p1, @patient1_pid, @patient1_uid, 14,   'DISPENSED', NOW() - INTERVAL 3 HOUR);

-- Backfill the FAILED row's failure_reason and the DISPENSED row's pharmacist link.
UPDATE refill_requests
SET failure_reason  = 'java.lang.IllegalArgumentException: quantity out of range — refill_requests row (constraint chk_quantity_range)',
    last_attempt_at = NOW() - INTERVAL 110 MINUTE
WHERE status = 'FAILED' AND failure_reason IS NULL;

UPDATE refill_requests
SET dispensed_at  = NOW() - INTERVAL 175 MINUTE,
    pharmacist_id = @pharmacist_uid
WHERE status = 'DISPENSED' AND dispensed_at IS NULL;
