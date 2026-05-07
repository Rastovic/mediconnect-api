-- [A02] Slabo hashovanje lozinki: MD5 bez salta.
-- MD5('admin123') = '0192023a7bbd73250516f069df18b500'
-- Lozinka se može otkriti pretraživanjem javnih rainbow tabela za MD5.

INSERT INTO users (username, email, password_hash, role, active)
VALUES ('admin', 'admin@mediconnect.com', '0192023a7bbd73250516f069df18b500', 'ADMIN', TRUE);

-- [A02] Seed pacijenti sa MD5 lozinkama
INSERT INTO users (username, email, password_hash, role, active) VALUES
    ('patient1', 'patient1@mediconnect.com', '827ccb0eea8a706c4c34a16891f84e7b', 'PATIENT', TRUE),   -- MD5('12345')
    ('doctor1',  'doctor1@mediconnect.com',  '5f4dcc3b5aa765d61d8327deb882cf99', 'DOCTOR',  TRUE);   -- MD5('password')

-- Seed pacijentski profil
INSERT INTO patients (user_id, insurance_number, date_of_birth, blood_type, allergies, emergency_contact)
VALUES (
    (SELECT id FROM users WHERE username = 'patient1'),
    'INS-987654321',
    '1990-05-15',
    'A+',
    'Penicillin, Pollen',
    'Jane Doe +387 61 111 222'
);

-- Seed doktorski profil
INSERT INTO doctors (user_id, specialty, license_number, hospital, phone)
VALUES (
    (SELECT id FROM users WHERE username = 'doctor1'),
    'General Practitioner',
    'LIC-2024-001',
    'MediConnect General Hospital',
    '+387 33 000 111'
);
