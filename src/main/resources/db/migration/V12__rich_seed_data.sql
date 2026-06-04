-- V12: rich additional data — more lab results, appointments, medical records,
--      prescriptions and messages for all accounts.
-- [A04] Passwords hashed with unsalted MD5 — intentional vulnerability.

-- ─── HELPER VARS — patient / doctor IDs ──────────────────────────────────────

SET @p1  = (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient1'));
SET @p2  = (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient2'));
SET @p3  = (SELECT id FROM patients WHERE user_id = (SELECT id FROM users WHERE username = 'patient3'));

SET @d1  = (SELECT id FROM doctors WHERE user_id = (SELECT id FROM users WHERE username = 'doctor1'));
SET @d2  = (SELECT id FROM doctors WHERE user_id = (SELECT id FROM users WHERE username = 'doctor2'));
SET @d3  = (SELECT id FROM doctors WHERE user_id = (SELECT id FROM users WHERE username = 'doctor3'));

SET @u_p1  = (SELECT id FROM users WHERE username = 'patient1');
SET @u_p2  = (SELECT id FROM users WHERE username = 'patient2');
SET @u_p3  = (SELECT id FROM users WHERE username = 'patient3');
SET @u_d1  = (SELECT id FROM users WHERE username = 'doctor1');
SET @u_d2  = (SELECT id FROM users WHERE username = 'doctor2');
SET @u_d3  = (SELECT id FROM users WHERE username = 'doctor3');
SET @u_lt  = (SELECT id FROM users WHERE username = 'labtech1');
SET @u_ph  = (SELECT id FROM users WHERE username = 'pharmacist1');

-- ─── ADDITIONAL APPOINTMENTS ─────────────────────────────────────────────────

INSERT INTO appointments (patient_id, doctor_id, status, requested_date, notes, created_at) VALUES

-- patient1 — follow-up GP (vitamin D / fatigue)
(@p1, @d1, 'APPROVED',   '2026-06-25 10:00:00', 'Follow-up for iron supplementation and vitamin D deficiency. Review CBC + 25-OH-D results.', '2026-06-10 08:30:00'),

-- patient1 — cardiology referral (GP referred after stress test)
(@p1, @d2, 'REQUESTED',  '2026-07-08 14:00:00', 'Referral from GP. Lipid panel borderline. Evaluate cardiovascular risk score.', '2026-06-20 09:00:00'),

-- patient2 — GP diabetes management
(@p2, @d1, 'APPROVED',   '2026-06-28 09:30:00', 'Diabetes quarterly check. Discuss HbA1c 7.4%, kidney function results, and metformin dose adjustment.', '2026-06-12 10:00:00'),

-- patient2 — cardiology referral (hypertension concern)
(@p2, @d2, 'REQUESTED',  '2026-07-15 11:00:00', 'GP referral — elevated BP readings (145/92 at last visit). Rule out secondary hypertension.', '2026-06-22 08:00:00'),

-- patient3 — GP follow-up after cardiac workup
(@p3, @d1, 'APPROVED',   '2026-07-02 09:00:00', 'Post-cardiology follow-up. Review Metoprolol tolerance and Holter monitor results.', '2026-06-21 11:00:00'),

-- patient3 — pediatrics (unrelated — child vaccination)
(@p3, @d3, 'REQUESTED',  '2026-07-20 10:30:00', 'Vaccination booster for younger sibling, age 4.', '2026-06-25 09:00:00'),

-- patient1 — past completed appointment (April, with doctor3 for minor issue)
(@p1, @d3, 'COMPLETED',  '2026-04-14 11:00:00', 'Mild respiratory infection. Prescribed rest and paracetamol.', '2026-04-10 08:00:00');

-- ─── ADDITIONAL LAB RESULTS ───────────────────────────────────────────────────

INSERT INTO lab_results (patient_id, lab_tech_id, test_name, result_value, unit, reference_range, status, test_date, notes) VALUES

-- patient1 — Fasting Blood Glucose
(@p1, @u_lt,
 'Fasting Blood Glucose',
 '5.2',
 'mmol/L',
 '3.9 – 5.6 normal, 5.6 – 6.9 impaired fasting, ≥ 7.0 diabetes',
 'COMPLETED',
 '2026-05-11 07:45:00',
 'Normal fasting glucose. No evidence of diabetes or prediabetes at this time.'),

-- patient1 — Urine Analysis
(@p1, @u_lt,
 'Urinalysis (Complete)',
 'pH 6.0, SG 1.018, Protein negative, Glucose negative, Leukocytes negative, Nitrites negative, Blood trace',
 '',
 'pH 4.5–8.0, SG 1.005–1.030, Protein < 150 mg/day, Glucose neg, WBC < 5/hpf',
 'COMPLETED',
 '2026-05-11 08:00:00',
 'Trace blood — likely menstrual contamination. Repeat in 6 weeks if persistent.'),

-- patient1 — Vitamin D (25-OH-D)
(@p1, @u_lt,
 'Vitamin D (25-hydroxyvitamin D)',
 '18.4',
 'ng/mL',
 '< 12 deficient, 12–20 insufficient, 20–50 sufficient, > 50 high',
 'COMPLETED',
 '2026-05-12 07:30:00',
 'Vitamin D insufficient. Supplementation 2000 IU/day recommended. Recheck in 3 months.'),

-- patient1 — Vitamin B12
(@p1, @u_lt,
 'Vitamin B12 (Cobalamin)',
 '298',
 'pg/mL',
 '200 – 900 pg/mL',
 'COMPLETED',
 '2026-05-12 07:35:00',
 'B12 low-normal. Monitor; consider supplementation if symptomatic fatigue persists after iron correction.'),

-- patient1 — Thyroid (TSH) — ordered by GP to rule out hypothyroid cause of fatigue
(@p1, @u_lt,
 'TSH (Thyroid Stimulating Hormone)',
 '2.8',
 'mIU/L',
 '0.4 – 4.0 mIU/L',
 'COMPLETED',
 '2026-05-12 07:40:00',
 'TSH within normal range. Hypothyroidism excluded as cause of fatigue.'),

-- patient1 — Ferritin (iron stores, ordered alongside CBC)
(@p1, @u_lt,
 'Serum Ferritin',
 '8',
 'ng/mL',
 'Women: 12 – 150 ng/mL, Men: 12 – 300 ng/mL',
 'COMPLETED',
 '2026-05-11 07:30:00',
 'Ferritin critically low — confirms iron deficiency. Continue Ferrous Sulphate supplementation.'),

-- patient2 — Kidney Function (eGFR + Creatinine) — diabetes monitoring
(@p2, @u_lt,
 'Kidney Function Panel (eGFR + Creatinine)',
 'Creatinine: 92, eGFR: 74',
 'µmol/L / mL/min/1.73m²',
 'Creatinine M: 62–106, eGFR: ≥ 90 normal, 60–89 mildly reduced',
 'COMPLETED',
 '2026-06-06 08:10:00',
 'eGFR mildly reduced (G2). Monitor every 6 months. Low protein diet counselling given.'),

-- patient2 — Urine Microalbumin (early nephropathy screen)
(@p2, @u_lt,
 'Urine Microalbumin : Creatinine Ratio',
 '38',
 'mg/g',
 '< 30 normal, 30–300 microalbuminuria, > 300 macroalbuminuria',
 'COMPLETED',
 '2026-06-06 08:15:00',
 'Borderline microalbuminuria. Indicates early diabetic kidney disease. ACE inhibitor consideration discussed with GP.'),

-- patient2 — Fasting Glucose
(@p2, @u_lt,
 'Fasting Blood Glucose',
 '8.6',
 'mmol/L',
 '3.9–5.6 normal, 5.6–6.9 impaired, ≥ 7.0 diabetes',
 'COMPLETED',
 '2026-06-06 07:50:00',
 'Elevated fasting glucose confirms poorly controlled T2DM. Correlates with HbA1c 7.4%.'),

-- patient2 — Complete Metabolic Panel (pending — ordered for upcoming appointment)
(@p2, @u_lt,
 'Complete Metabolic Panel (CMP)',
 NULL,
 'various',
 'Na 136–145, K 3.5–5.1, Cl 98–107, CO2 22–29, BUN 7–20, Cr 0.6–1.2, Glucose 70–99, Ca 8.5–10.2',
 'PENDING',
 '2026-06-26 07:30:00',
 'Pre-appointment panel. Ordered to assess electrolyte balance on current medication regimen.'),

-- patient2 — Lipid Panel (cardiovascular risk with diabetes)
(@p2, @u_lt,
 'Lipid Panel',
 'TC: 224, LDL: 148, HDL: 38, TG: 190',
 'mg/dL',
 'TC: < 200, LDL: < 130, HDL: > 40, TG: < 150',
 'COMPLETED',
 '2026-06-06 08:05:00',
 'Dyslipidaemia — elevated LDL and TG, low HDL. Statin therapy recommended. Dietary modification advised.'),

-- patient3 — Holter 24h (SVT evaluation)
(@p3, @u_lt,
 'Holter Monitor 24h ECG',
 'Sinus rhythm predominant. 3 episodes of SVT (longest 14 sec, HR 178 bpm). No VT/VF detected.',
 '',
 'Normal: sinus rhythm, no sustained arrhythmia',
 'COMPLETED',
 '2026-06-21 09:00:00',
 'SVT confirmed. Episodes self-terminating. Metoprolol dose adequate. Electrophysiology referral if recurrent.'),

-- patient3 — D-dimer (rule out pulmonary embolism during palpitation workup)
(@p3, @u_lt,
 'D-dimer',
 '0.38',
 'mg/L FEU',
 '< 0.50 mg/L (low probability of VTE)',
 'COMPLETED',
 '2026-06-18 08:00:00',
 'D-dimer within normal range. Pulmonary embolism unlikely. No further imaging required.'),

-- patient3 — BNP (heart failure marker, ordered by cardiologist)
(@p3, @u_lt,
 'BNP (B-type Natriuretic Peptide)',
 '42',
 'pg/mL',
 '< 100 pg/mL: heart failure unlikely',
 'COMPLETED',
 '2026-06-18 08:05:00',
 'BNP normal. No evidence of heart failure. SVT likely primary arrhythmia without structural cause.');

-- ─── ADDITIONAL MEDICAL RECORDS ──────────────────────────────────────────────

-- patient1 — Vitamin D deficiency (from GP follow-up)
INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    @p1, @d1, NULL,
    'Vitamin D insufficiency (25-OH-D 18.4 ng/mL). Iron-deficiency anaemia improving on supplementation (Hgb up to 10.8 from 9.8). Vitamin B12 low-normal.',
    'Vitamin D3 2000 IU daily for 3 months. Continue Ferrous Sulphate. Repeat 25-OH-D and CBC in 12 weeks. Dietary advice: oily fish, eggs, fortified foods.',
    '2026-06-25 10:45:00'
);

-- patient1 — Respiratory infection (April, doctor3)
SET @apt_p1_d3 = (
    SELECT a.id FROM appointments a
    JOIN patients p ON a.patient_id = p.id
    JOIN doctors  d ON a.doctor_id  = d.id
    JOIN users up ON p.user_id = up.id
    JOIN users ud ON d.user_id  = ud.id
    WHERE up.username = 'patient1' AND ud.username = 'doctor3' AND a.status = 'COMPLETED'
    LIMIT 1
);

INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    @p1, @d3, @apt_p1_d3,
    'Acute viral upper respiratory tract infection. Mild pharyngitis, no bacterial signs. Temperature 37.8°C.',
    'Paracetamol 500 mg every 6 hours as needed. Adequate hydration and rest. Honey-lemon gargle for throat. Return if fever > 38.5°C or no improvement in 5 days.',
    '2026-04-14 11:30:00'
);

-- patient2 — Diabetes management + dyslipidaemia
INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    @p2, @d1, NULL,
    'Type 2 diabetes mellitus — suboptimally controlled (HbA1c 7.4%). Dyslipidaemia: LDL 148, TG 190, HDL 38. Borderline microalbuminuria suggesting early diabetic nephropathy. Mildly reduced eGFR 74.',
    'Increase Metformin to 1000 mg twice daily. Add Atorvastatin 20 mg once daily at night. Ramipril 2.5 mg once daily (renal protection). Low-carbohydrate, low-saturated-fat diet. Repeat HbA1c and lipid panel in 3 months.',
    '2026-06-28 10:00:00'
);

-- patient3 — Post-Holter cardiology review
INSERT INTO medical_records (patient_id, doctor_id, appointment_id, diagnosis, prescription, created_at)
VALUES (
    @p3, @d2, NULL,
    'Paroxysmal supraventricular tachycardia (PSVT) confirmed on 24h Holter — 3 self-terminating episodes (max HR 178 bpm, 14 seconds). No structural heart disease. BNP normal. Ejection fraction preserved.',
    'Continue Metoprolol 25 mg once daily. Taught vagal manoeuvres (Valsalva, carotid sinus massage). Avoid caffeine, alcohol, dehydration. Return immediately if episode > 30 minutes or haemodynamic compromise. Electrophysiology referral if frequency increases.',
    '2026-07-02 09:45:00'
);

-- ─── ADDITIONAL PRESCRIPTIONS ────────────────────────────────────────────────

SET @rec_p1_vit = (
    SELECT id FROM medical_records
    WHERE patient_id = @p1 AND diagnosis LIKE '%Vitamin D%'
    LIMIT 1
);

-- Vitamin D3 for patient1 — dispensed by pharmacist1
INSERT INTO prescriptions (medical_record_id, patient_id, doctor_id, pharmacist_id, medication_name, dosage, instructions, status, created_at, dispensed_at)
VALUES (
    @rec_p1_vit, @p1, @d1, @u_ph,
    'Vitamin D3 (Cholecalciferol)',
    '2000 IU',
    'Take one capsule daily with the largest meal of the day for better absorption. Do not exceed 4000 IU/day without medical advice.',
    'DISPENSED',
    '2026-06-25 11:00:00',
    '2026-06-26 10:15:00'
);

SET @rec_p2_dm = (
    SELECT id FROM medical_records
    WHERE patient_id = @p2 AND diagnosis LIKE '%Type 2 diabetes%'
    LIMIT 1
);

-- Metformin 1000 mg for patient2 (created, not yet dispensed)
INSERT INTO prescriptions (medical_record_id, patient_id, doctor_id, medication_name, dosage, instructions, status, created_at)
VALUES (
    @rec_p2_dm, @p2, @d1,
    'Metformin Hydrochloride',
    '1000 mg',
    'Take one tablet twice daily with breakfast and evening meal. May cause GI upset initially — take with food. Hold 48 hours before any contrast imaging.',
    'CREATED',
    '2026-06-28 10:05:00'
);

-- Atorvastatin for patient2
INSERT INTO prescriptions (medical_record_id, patient_id, doctor_id, medication_name, dosage, instructions, status, created_at)
VALUES (
    @rec_p2_dm, @p2, @d1,
    'Atorvastatin',
    '20 mg',
    'Take one tablet once daily in the evening. Report any unexplained muscle pain or weakness. Avoid grapefruit juice.',
    'CREATED',
    '2026-06-28 10:06:00'
);

-- Ramipril for patient2
INSERT INTO prescriptions (medical_record_id, patient_id, doctor_id, medication_name, dosage, instructions, status, created_at)
VALUES (
    @rec_p2_dm, @p2, @d1,
    'Ramipril',
    '2.5 mg',
    'Take one tablet once daily in the morning. Monitor blood pressure and potassium after 2 weeks. Report dry cough — may require switching to ARB.',
    'CREATED',
    '2026-06-28 10:07:00'
);

-- ─── ADDITIONAL MESSAGES ─────────────────────────────────────────────────────

INSERT INTO messages (sender_id, receiver_id, content, sent_at, read_at) VALUES

-- patient1 → doctor2 (cardiology appointment question)
(@u_p1, @u_d2,
 'Dear Dr. Vasić, I was referred to you by Dr. Marić for a cardiovascular risk assessment. My lipid panel showed TC 201, LDL 128. Should I be concerned? I am 34 years old and do not smoke.',
 '2026-06-22 14:00:00',
 '2026-06-22 16:30:00'),

-- doctor2 → patient1 (reply)
(@u_d2, @u_p1,
 'Hello Ana, thank you for reaching out. At your age, an LDL of 128 with no other risk factors is borderline but not alarming. We will get a full picture at your appointment on 8 July — please fast for 12 hours before and bring your previous results. See you then.',
 '2026-06-22 16:35:00',
 '2026-06-23 08:00:00'),

-- pharmacist1 → patient1 (Vitamin D prescription ready)
(@u_ph, @u_p1,
 'Good morning Ana, your Vitamin D3 prescription from Dr. Marić is ready for collection at the MediConnect Pharmacy, ground floor. Opening hours 08:00–18:00 Monday to Saturday.',
 '2026-06-26 09:00:00',
 '2026-06-26 10:00:00'),

-- patient1 → pharmacist1 (acknowledgement)
(@u_p1, @u_ph,
 'Thank you! I will pick it up today around noon.',
 '2026-06-26 10:05:00',
 '2026-06-26 10:10:00'),

-- doctor1 → patient2 (lab results explanation)
(@u_d1, @u_p2,
 'Marko, I have reviewed your latest results. Your HbA1c has increased to 7.4% and your LDL is elevated at 148. I will be increasing your Metformin and starting you on a statin at our appointment on 28 June. Please fast from midnight the night before so we can repeat your glucose.',
 '2026-06-10 11:00:00',
 '2026-06-10 13:00:00'),

-- patient2 → doctor1 (reply about symptoms)
(@u_p2, @u_d1,
 'Dr. Marić, understood. I have also noticed some swelling in my feet in the evenings. Could this be related to my kidneys? The lab tech mentioned my eGFR was 74.',
 '2026-06-10 13:30:00',
 '2026-06-11 09:00:00'),

-- doctor1 → patient2 (reassurance)
(@u_d1, @u_p2,
 'Marko, eGFR of 74 is mildly reduced — it warrants monitoring but is not alarming at this stage. The ankle swelling may be related to circulation or salt intake rather than kidney failure. We will address all of this at your appointment. Track your blood pressure daily until then.',
 '2026-06-11 09:15:00',
 '2026-06-11 10:00:00'),

-- patient3 → doctor2 (Holter results question)
(@u_p3, @u_d2,
 'Dr. Vasić, I received the Holter report. It says I had 3 SVT episodes. I am quite worried — does this mean my heart is dangerous? I felt very anxious during the recording.',
 '2026-06-21 20:00:00',
 '2026-06-22 08:00:00'),

-- doctor2 → patient3 (reassurance)
(@u_d2, @u_p3,
 'Sofija, I understand the worry. Three short, self-terminating episodes is relatively common with SVT and does not mean your heart is in immediate danger. We will discuss everything thoroughly at your appointment on 2 July. In the meantime, try limiting caffeine and getting adequate sleep — both are known triggers. Call the emergency line if an episode lasts more than 30 minutes.',
 '2026-06-22 08:05:00',
 '2026-06-22 09:30:00'),

-- doctor3 → patient2 (following up on child vaccination appointment)
(@u_d3, @u_p2,
 'Dear Mr. Petrović, this is a reminder that your child vaccination appointment is scheduled for 20 July at 10:30. Please bring the yellow vaccination booklet. If the child has a fever or infection on that day, please reschedule.',
 '2026-07-10 09:00:00',
 NULL),

-- admin → doctor1 (system notification style)
((SELECT id FROM users WHERE username = 'admin'), @u_d1,
 'System notice: scheduled maintenance window on 5 July 02:00–04:00. Patient records will be read-only during this period.',
 '2026-06-30 10:00:00',
 NULL);
