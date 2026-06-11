-- V17: Cascade every users(id) foreign-key reference.
--
-- [A09] Required so DELETE /api/admin/users/{id} (vuln #148) and
--        POST /api/admin/users/bulk-delete (vuln #152) actually succeed
--        instead of throwing DataIntegrityViolationException on the FK.
--
-- The "fix" itself is the demo: cascading every user FK turns a single
-- DELETE into a clinical-data wipe.
--
--   audit_logs       — evidence destruction (A09 compound)
--   patients/doctors — user profiles deleted alongside the account
--   messages         — both sides of every conversation involving the user
--   prescriptions    — every Rx the user dispensed (pharmacist_id FK)
--   lab_results      — every result the user certified (lab_tech_id FK)
--   refill_requests  — every refill the user dispensed
--
-- All target columns are NOT NULL in their JPA entities (Message.sender,
-- LabResult.labTech, etc.), so SET NULL is not an option without an entity
-- rewrite. CASCADE keeps the schema demoable with no Java changes.
--
-- Real attack: a PATIENT calls DELETE /api/admin/users/{adminId} —
-- the admin account, every audit entry, every administrative action and
-- every clinical record they ever touched vanish in one request.

ALTER TABLE audit_logs       DROP FOREIGN KEY fk_audit_user;
ALTER TABLE audit_logs       ADD CONSTRAINT fk_audit_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE patients         DROP FOREIGN KEY fk_patients_user;
ALTER TABLE patients         ADD CONSTRAINT fk_patients_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE doctors          DROP FOREIGN KEY fk_doctors_user;
ALTER TABLE doctors          ADD CONSTRAINT fk_doctors_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE messages         DROP FOREIGN KEY fk_msg_sender;
ALTER TABLE messages         ADD CONSTRAINT fk_msg_sender
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE messages         DROP FOREIGN KEY fk_msg_receiver;
ALTER TABLE messages         ADD CONSTRAINT fk_msg_receiver
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE prescriptions    DROP FOREIGN KEY fk_rx_pharmacist;
ALTER TABLE prescriptions    ADD CONSTRAINT fk_rx_pharmacist
    FOREIGN KEY (pharmacist_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE lab_results      DROP FOREIGN KEY fk_lab_tech_user;
ALTER TABLE lab_results      ADD CONSTRAINT fk_lab_tech_user
    FOREIGN KEY (lab_tech_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE refill_requests  DROP FOREIGN KEY fk_refill_pharmacist;
ALTER TABLE refill_requests  ADD CONSTRAINT fk_refill_pharmacist
    FOREIGN KEY (pharmacist_id) REFERENCES users(id) ON DELETE CASCADE;
