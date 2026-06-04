-- Extended seed: 3 patients, 3 doctors, 1 lab tech, 1 pharmacist + full relational data.
-- [A04] All passwords hashed with unsalted MD5 — intentional vulnerability.

-- ─── USERS ───────────────────────────────────────────────────────────────────

INSERT INTO users (username, email, password_hash, role, active) VALUES
('patient2',     'marko.petrovic@mail.com',       'c63f24079f1d5e4cae3fdc1a29116a7b', 'PATIENT',     TRUE),  -- patient123
('patient3',     'sofija.nikolic@mail.com',        'c63f24079f1d5e4cae3fdc1a29116a7b', 'PATIENT',     TRUE),  -- patient123
('doctor2',      'elena.vasic@mediconnect.com',    'b3666d14ca079417ba6c2a99f079b2ac', 'DOCTOR',      TRUE),  -- doctor123
('doctor3',      'tomislav.horvatic@mediconnect.com', 'b3666d14ca079417ba6c2a99f079b2ac', 'DOCTOR',   TRUE),  -- doctor123
('labtech1',     'labtech1@mediconnect.com',       '4e0b530dd4f8232dae6cc01753b156df', 'LAB_TECH',    TRUE),  -- labtech123
('pharmacist1',  'pharma1@mediconnect.com',        '2bfa6dce52ee8cf7a9fd149c999ca1db', 'PHARMACIST',  TRUE);  -- pharma123

-- ─── PATIENT PROFILES ────────────────────────────────────────────────────────

INSERT INTO patients (user_id, insurance_number, date_of_birth, blood_type, allergies, emergency_contact)
VALUES
(
    (SELECT id FROM users WHERE username = 'patient2'),
    'INS-112233445',
    '1985-03-22',
    'B+',
    'Ibuprofen, Latex',
    'Petra Petrović +387 62 333 444'
),
(
    (SELECT id FROM users WHERE username = 'patient3'),
    'INS-556677889',
    '1998-11-07',
    'O-',
    'Penicillin',
    'Ivan Nikolić +387 63 555 666'
);

-- ─── DOCTOR PROFILES ─────────────────────────────────────────────────────────

UPDATE doctors
   SET bio = 'General practitioner with 8 years of primary care experience. Focuses on preventive medicine and chronic disease management.'
 WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1');

INSERT INTO doctors (user_id, specialty, license_number, hospital, phone, bio)
VALUES
(
    (SELECT id FROM users WHERE username = 'doctor2'),
    'Cardiology',
    'LIC-2024-002',
    'MediConnect Heart Center',
    '+387 33 000 222',
    'Board-certified cardiologist with 12 years of experience in interventional cardiology and heart failure management.'
),
(
    (SELECT id FROM users WHERE username = 'doctor3'),
    'Pediatrics',
    'LIC-2024-003',
    'MediConnect Children Hospital',
    '+387 33 000 333',
    'Pediatrician specialising in neonatal care and childhood development disorders. Fluent in English, German, and Croatian.'
);

-- ─── APPOINTMENTS ────────────────────────────────────────────────────────────

INSERT INTO appointments (patient_id, doctor_id, status, requested_date, notes, created_at)
VALUES
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1')),
    'COMPLETED', '2026-05-10 09:00:00',
    'Annual checkup. Patient reported persistent fatigue and mild headaches for 3 weeks.',
    '2026-05-01 08:00:00'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor2')),
    'APPROVED', '2026-06-15 11:30:00',
    'Follow-up ECG and stress test as recommended after GP visit.',
    '2026-06-01 10:00:00'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient2')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1')),
    'REQUESTED', '2026-06-20 14:00:00',
    'Patient requests referral for endocrinology — managing Type 2 diabetes.',
    '2026-06-05 09:30:00'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient2')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor3')),
    'COMPLETED', '2026-04-28 10:00:00',
    'Child health consultation for patient''s son, age 6. Routine vaccination review.',
    '2026-04-20 08:00:00'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient3')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor2')),
    'APPROVED', '2026-06-18 09:00:00',
    'First cardiology consultation. Family history of heart disease. Palpitations last month.',
    '2026-06-03 11:00:00'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient3')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1')),
    'CANCELLED', '2026-05-25 15:00:00',
    'Patient cancelled due to travel.',
    '2026-05-10 09:00:00'
);

-- ─── MEDICAL RECORDS ─────────────────────────────────────────────────────────

-- Record for patient1/doctor1 completed appointment
SET @apt_p1_d1 = (
    SELECT a.id FROM appointments a
    JOIN patients p ON a.patient_id = p.id
    JOIN doctors  d ON a.doctor_id  = d.id
    JOIN users up ON p.user_id = up.id
    JOIN users ud ON d.user_id  = ud.id
    WHERE up.username = 'patient1' AND ud.username = 'doctor1' AND a.status = 'COMPLETED'
    LIMIT 1
);

INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1')),
    @apt_p1_d1,
    'Iron-deficiency anaemia. Mild tension-type headaches secondary to anaemia. Blood pressure 118/76 mmHg.',
    'Ferrous sulphate 200 mg twice daily for 3 months. Paracetamol 500 mg PRN for headaches. Repeat CBC in 6 weeks.',
    '2026-05-10 09:45:00'
);

-- Record for patient2/doctor3 completed appointment
SET @apt_p2_d3 = (
    SELECT a.id FROM appointments a
    JOIN patients p ON a.patient_id = p.id
    JOIN doctors  d ON a.doctor_id  = d.id
    JOIN users up ON p.user_id = up.id
    JOIN users ud ON d.user_id  = ud.id
    WHERE up.username = 'patient2' AND ud.username = 'doctor3' AND a.status = 'COMPLETED'
    LIMIT 1
);

INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient2')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor3')),
    @apt_p2_d3,
    'Child (age 6) is healthy and on track developmentally. MMR booster administered. No adverse reactions noted.',
    'No medication required. Return in 12 months for next scheduled vaccination.',
    '2026-04-28 10:40:00'
);

-- Standalone record for patient3 (not tied to a specific appointment)
INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient3')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor2')),
    NULL,
    'Suspected paroxysmal supraventricular tachycardia. Holter monitor ordered. No structural abnormality on echo.',
    'Metoprolol 25 mg once daily. Avoid caffeine and alcohol. Return in 4 weeks with Holter results.',
    '2026-06-18 09:50:00'
);

-- ─── PRESCRIPTIONS ───────────────────────────────────────────────────────────

SET @rec_p1 = (
    SELECT mr.id FROM medical_records mr
    JOIN patients p ON mr.patient_id = p.id
    JOIN users u ON p.user_id = u.id
    WHERE u.username = 'patient1'
    LIMIT 1
);

INSERT INTO prescriptions (medical_record_id, patient_id, doctor_id, medication_name, dosage, instructions, status, created_at)
VALUES (
    @rec_p1,
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1')),
    'Ferrous Sulphate',
    '200 mg',
    'Take one tablet twice daily with meals. Avoid dairy products within 2 hours of dose. Do not crush or chew.',
    'CREATED',
    '2026-05-10 09:50:00'
);

SET @rec_p3 = (
    SELECT mr.id FROM medical_records mr
    JOIN patients p ON mr.patient_id = p.id
    JOIN users u ON p.user_id = u.id
    WHERE u.username = 'patient3'
    LIMIT 1
);

SET @pharmacist_uid = (SELECT id FROM users WHERE username = 'pharmacist1');

INSERT INTO prescriptions (medical_record_id, patient_id, doctor_id, pharmacist_id, medication_name, dosage, instructions, status, created_at, dispensed_at)
VALUES (
    @rec_p3,
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient3')),
    (SELECT id FROM doctors  WHERE user_id = (SELECT id FROM users WHERE username = 'doctor2')),
    @pharmacist_uid,
    'Metoprolol Tartrate',
    '25 mg',
    'Take one tablet once daily in the morning with or without food. Do not stop abruptly — taper under physician supervision.',
    'DISPENSED',
    '2026-06-18 10:00:00',
    '2026-06-19 14:30:00'
);

-- ─── LAB RESULTS ─────────────────────────────────────────────────────────────

SET @labtech_uid = (SELECT id FROM users WHERE username = 'labtech1');

INSERT INTO lab_results (patient_id, lab_tech_id, test_name, result_value, unit, reference_range, status, test_date, notes)
VALUES
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1')),
    @labtech_uid,
    'Complete Blood Count (CBC)',
    'Hgb: 9.8, MCV: 71, WBC: 6.2, Plt: 280',
    'g/dL / fL / 10^9/L',
    'Hgb: 12–16, MCV: 80–100, WBC: 4–10, Plt: 150–400',
    'COMPLETED',
    '2026-05-11 07:30:00',
    'Low haemoglobin and MCV consistent with iron-deficiency anaemia. Repeat in 6 weeks.'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1')),
    @labtech_uid,
    'Lipid Panel',
    'TC: 201, LDL: 128, HDL: 52, TG: 105',
    'mg/dL',
    'TC: <200, LDL: <130, HDL: >40, TG: <150',
    'PENDING',
    '2026-06-10 07:00:00',
    'Ordered by cardiologist ahead of stress test appointment.'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient2')),
    @labtech_uid,
    'HbA1c (Glycated Haemoglobin)',
    '7.4',
    '%',
    '< 5.7 normal, 5.7–6.4 prediabetes, ≥ 6.5 diabetes',
    'COMPLETED',
    '2026-06-06 08:00:00',
    'HbA1c 7.4% — above target for managed T2DM (goal < 7.0%). Lifestyle counselling recommended.'
),
(
    (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient3')),
    @labtech_uid,
    'Thyroid Function (TSH, fT4)',
    'TSH: 1.9, fT4: 14.2',
    'mIU/L / pmol/L',
    'TSH: 0.4–4.0, fT4: 9–25',
    'COMPLETED',
    '2026-06-19 07:30:00',
    'Thyroid function within normal range. Palpitations not of thyroid origin.'
);

-- ─── MESSAGES ────────────────────────────────────────────────────────────────

INSERT INTO messages (sender_id, receiver_id, content, sent_at, read_at)
VALUES
(
    (SELECT id FROM users WHERE username = 'patient1'),
    (SELECT id FROM users WHERE username = 'doctor1'),
    'Good morning Dr. Marić, I wanted to let you know I have started the iron tablets. I feel a little better already. Should I take them with orange juice as I read online?',
    '2026-05-14 10:15:00',
    '2026-05-14 11:02:00'
),
(
    (SELECT id FROM users WHERE username = 'doctor1'),
    (SELECT id FROM users WHERE username = 'patient1'),
    'Hello Ana, glad to hear you are feeling better. Yes, vitamin C (orange juice) improves iron absorption — good initiative. Avoid tea or coffee within an hour of the dose. See you at the follow-up.',
    '2026-05-14 11:05:00',
    '2026-05-14 12:30:00'
),
(
    (SELECT id FROM users WHERE username = 'patient2'),
    (SELECT id FROM users WHERE username = 'doctor1'),
    'Dr. Marić, my last HbA1c came back at 7.4%. My previous GP had me at 6.9%. Can we discuss adjusting my metformin dose at the next visit?',
    '2026-06-07 09:00:00',
    NULL
),
(
    (SELECT id FROM users WHERE username = 'doctor2'),
    (SELECT id FROM users WHERE username = 'patient3'),
    'Dear Ms. Nikolić, your Holter monitor kit is ready for collection at the Heart Center reception. Please wear it for 24 hours on a typical workday. Bring it back the following morning.',
    '2026-06-20 08:00:00',
    NULL
);
