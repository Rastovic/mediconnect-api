-- V24: Module F (Doctor View Redesign Phase 6) — referrals + AI-verified flag.
--
-- `referrals` carries a base64-encoded Java ObjectOutputStream blob in
-- `bundle_payload`. The inbox + accept endpoints call
-- `ObjectInputStream.readObject()` against the decoded bytes with no class
-- allow-list — textbook deserialisation RCE.
--
-- `medical_records.ai_verified` is the integrity-loss flag set by
-- POST /ai/summarize-record. Frontend chart renders rows with a green
-- "AI verified" check whenever this column is true, with no provenance.
--
-- `medical_records.notes` makes the column the service writes to actually
-- exist (the existing schema only has `prescription` as a free-text field;
-- we keep using it for new AI summaries to avoid a wider schema change).

CREATE TABLE referrals (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    from_doctor_id  BIGINT,
    to_doctor_id    BIGINT,
    patient_id      BIGINT,
    subject         VARCHAR(255),
    bundle_payload  MEDIUMTEXT,
    accepted        BOOLEAN      NOT NULL DEFAULT FALSE,
    accepted_at     DATETIME,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_referrals_from_doctor
        FOREIGN KEY (from_doctor_id) REFERENCES doctors(id) ON DELETE SET NULL,
    CONSTRAINT fk_referrals_to_doctor
        FOREIGN KEY (to_doctor_id) REFERENCES doctors(id) ON DELETE SET NULL,
    CONSTRAINT fk_referrals_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_referrals_to_doctor ON referrals (to_doctor_id);
CREATE INDEX idx_referrals_accepted  ON referrals (accepted);

ALTER TABLE medical_records
    ADD COLUMN ai_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_model_url VARCHAR(2048) NULL;

-- Seed one referral pointing doctor 2 -> doctor 1 with a benign payload so
-- the inbox page lights up out of the box.
INSERT INTO referrals (from_doctor_id, to_doctor_id, patient_id, subject, bundle_payload, accepted, created_at)
VALUES (2, 1, 1, 'Cardiology follow-up review',
        'BENIGN_PLACEHOLDER',
        FALSE, '2026-06-11 10:00:00');
