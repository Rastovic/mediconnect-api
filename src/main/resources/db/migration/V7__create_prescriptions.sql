CREATE TABLE prescriptions (
    id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
    medical_record_id BIGINT       NOT NULL,
    patient_id        BIGINT       NOT NULL,
    doctor_id         BIGINT       NOT NULL,
    pharmacist_id     BIGINT,
    medication_name   VARCHAR(255) NOT NULL,
    dosage            VARCHAR(100),
    instructions      TEXT,
    status            ENUM('CREATED','DISPENSED','CANCELLED') DEFAULT 'CREATED',
    created_at        TIMESTAMP    DEFAULT NOW(),
    dispensed_at      TIMESTAMP    NULL,
    CONSTRAINT fk_rx_record     FOREIGN KEY (medical_record_id) REFERENCES medical_records(id),
    CONSTRAINT fk_rx_patient    FOREIGN KEY (patient_id)        REFERENCES patients(id),
    CONSTRAINT fk_rx_doctor     FOREIGN KEY (doctor_id)         REFERENCES doctors(id),
    CONSTRAINT fk_rx_pharmacist FOREIGN KEY (pharmacist_id)     REFERENCES users(id)
);
