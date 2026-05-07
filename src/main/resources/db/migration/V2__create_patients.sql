-- [A04] Insecure Design: svi PII podaci pohranjeni kao plaintext bez enkripcije.
-- insurance_number, date_of_birth, blood_type, allergies i emergency_contact
-- trebali bi biti enkriptovani u bazi ili na aplikacijskom sloju.
CREATE TABLE patients (
    id                BIGINT        PRIMARY KEY AUTO_INCREMENT,
    user_id           BIGINT        UNIQUE NOT NULL,
    insurance_number  VARCHAR(50),
    date_of_birth     DATE,
    blood_type        VARCHAR(5),
    allergies         TEXT,
    emergency_contact VARCHAR(255),
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users(id)
);
