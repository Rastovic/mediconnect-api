-- V18: Cascade clinical-entity foreign keys so the Module E delete endpoints
--       actually succeed instead of throwing DataIntegrityViolationException.
--
-- [A09] Required so:
--   - DELETE /api/admin/medical-records/{id} (vuln #176 — Phase 4 / Module E)
--     actually removes the record + its child prescriptions.
--   - PUT /api/admin/prescriptions/{id} status=CANCELLED followed by a
--     downstream delete works without dangling refill_requests.
--
-- The CASCADE itself is the demo: deleting one medical_record wipes every
-- prescription that referenced it, and (transitively) every refill_request
-- on those prescriptions. A single endpoint call erases the patient's entire
-- clinical history from a given encounter.
--
-- Same trade-off as V17: real systems would archive, not cascade. The
-- vulnerable branch cascades on purpose to make the demo land.

ALTER TABLE prescriptions   DROP FOREIGN KEY fk_rx_record;
ALTER TABLE prescriptions   ADD CONSTRAINT fk_rx_record
    FOREIGN KEY (medical_record_id) REFERENCES medical_records(id) ON DELETE CASCADE;

ALTER TABLE refill_requests DROP FOREIGN KEY fk_refill_prescription;
ALTER TABLE refill_requests ADD CONSTRAINT fk_refill_prescription
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE;
