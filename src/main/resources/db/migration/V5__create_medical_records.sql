-- [A08] Software and Data Integrity Failures: nema content_hash kolone.
-- Bez hash-a fajla nije moguće detektovati neautorizovanu izmjenu attachment-a
-- niti verificirati integritet medicinskih nalaza (diagnosis, prescription).
CREATE TABLE medical_records (
    id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
    patient_id     BIGINT       NOT NULL,
    doctor_id      BIGINT       NOT NULL,
    appointment_id BIGINT,
    diagnosis      TEXT,
    prescription   TEXT,
    attachment_path VARCHAR(500),
    created_at     TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT fk_records_patient     FOREIGN KEY (patient_id)     REFERENCES patients(id),
    CONSTRAINT fk_records_doctor      FOREIGN KEY (doctor_id)      REFERENCES doctors(id),
    CONSTRAINT fk_records_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id)
);
