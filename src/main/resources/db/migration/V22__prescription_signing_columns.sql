-- V22: Extend `prescriptions` for Module D (Doctor View Redesign Phase 4).
--
-- Adds columns the new e-prescribing flow needs:
--   * pharmacy_callback_url — caller-supplied URL the server POSTs to at
--     create time (SSRF target preserved on the row).
--   * signature_md5 / signed_at — MD5 + plaintext key chain used by
--     POST /api/doctor/prescriptions/{id}/sign.
--   * signature_jwt / co_signer_username — JWT (alg=none accepted) +
--     decoded `sub` from POST /api/doctor/prescriptions/{id}/co-sign.
--   * ai_verified flag — used in Module F to mark records as
--     "authoritative" without any provenance check; added here so the
--     Prescription chart row can carry it too.
--
-- medical_record_id is relaxed to nullable so the new /api/doctor/prescriptions
-- endpoint can issue standalone prescriptions without first creating an
-- appointment + medical record. Existing rows already have a non-null value
-- so no backfill is required.

ALTER TABLE prescriptions
    MODIFY COLUMN medical_record_id BIGINT NULL,
    ADD COLUMN pharmacy_callback_url VARCHAR(2048) NULL,
    ADD COLUMN signature_md5         VARCHAR(64)   NULL,
    ADD COLUMN signed_at             DATETIME      NULL,
    ADD COLUMN signature_jwt         VARCHAR(2048) NULL,
    ADD COLUMN co_signer_username    VARCHAR(100)  NULL,
    ADD COLUMN ai_verified           BOOLEAN       NOT NULL DEFAULT FALSE;
