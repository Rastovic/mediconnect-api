CREATE TABLE appointments (
    id             BIGINT    PRIMARY KEY AUTO_INCREMENT,
    patient_id     BIGINT    NOT NULL,
    doctor_id      BIGINT    NOT NULL,
    status         ENUM('REQUESTED','APPROVED','CANCELLED','COMPLETED') DEFAULT 'REQUESTED',
    requested_date DATETIME  NOT NULL,
    notes          TEXT,
    created_at     TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_appointments_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors(id)
);
