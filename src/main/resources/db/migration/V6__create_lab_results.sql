CREATE TABLE lab_results (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    patient_id      BIGINT       NOT NULL,
    lab_tech_id     BIGINT       NOT NULL,
    test_name       VARCHAR(255) NOT NULL,
    result_value    TEXT,
    unit            VARCHAR(50),
    reference_range VARCHAR(100),
    status          ENUM('PENDING','COMPLETED','CANCELLED') DEFAULT 'PENDING',
    test_date       TIMESTAMP    DEFAULT NOW(),
    notes           TEXT,
    attachment_path VARCHAR(500),
    CONSTRAINT fk_lab_patient   FOREIGN KEY (patient_id)  REFERENCES patients(id),
    CONSTRAINT fk_lab_tech_user FOREIGN KEY (lab_tech_id) REFERENCES users(id)
);
