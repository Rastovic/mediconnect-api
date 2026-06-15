-- V20: Clinical notes table for Module B (Doctor View Redesign Phase 2).
--
-- The table is intentionally minimal — no `version`, no `previous_revision_id`,
-- no `edited_by`, no soft-delete column. PUT /api/doctor/notes/{id} overwrites
-- the row in place ([A08] integrity loss) and DELETE hard-deletes ([A09]
-- audit-trail loss). The columns below are exactly the fields the demo needs;
-- the missing ones are the demo.
--
-- `template_name` is what the caller supplies on create. DoctorNoteService
-- resolves it to a path under notes/templates/ with no normalisation, so a
-- traversal payload reads outside the template directory. The rendered HTML
-- is stored verbatim in `rendered_html` and later sent to the frontend,
-- which drops it into the DOM via `dangerouslySetInnerHTML`.
--
-- `signature` holds the (intentionally weak) JWT supplied at co-sign time —
-- the service accepts `alg: none` and trusts the `sub` claim verbatim.

CREATE TABLE clinical_notes (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id      BIGINT       NOT NULL,
    doctor_id       BIGINT,
    template_name   VARCHAR(200) NOT NULL,
    raw_data        TEXT,
    rendered_html   MEDIUMTEXT,
    signature       VARCHAR(2048),
    signer_username VARCHAR(100),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_clinical_notes_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    CONSTRAINT fk_clinical_notes_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_clinical_notes_patient ON clinical_notes (patient_id);
CREATE INDEX idx_clinical_notes_doctor  ON clinical_notes (doctor_id);

-- Seed two notes for patient 1 so the Notes page is populated out of the box.
INSERT INTO clinical_notes (patient_id, doctor_id, template_name, raw_data, rendered_html, created_at, updated_at)
VALUES
  (1, 1, 'soap',     '{"subjective":"Patient reports fatigue and joint pain","objective":"BP 130/85, HR 78, joints unremarkable","assessment":"Likely viral, monitor","plan":"Rest, fluids, follow-up in 7 days"}',
                     '<h3>SOAP Note</h3><p><b>S:</b> Patient reports fatigue and joint pain</p><p><b>O:</b> BP 130/85, HR 78, joints unremarkable</p><p><b>A:</b> Likely viral, monitor</p><p><b>P:</b> Rest, fluids, follow-up in 7 days</p>',
                     '2026-05-12 09:30:00', '2026-05-12 09:30:00'),
  (1, 1, 'progress', '{"summary":"Iron supplementation tolerated well; haemoglobin trending up","next":"Repeat FBC in 4 weeks"}',
                     '<h3>Progress Note</h3><p>Iron supplementation tolerated well; haemoglobin trending up</p><p>Next: Repeat FBC in 4 weeks</p>',
                     '2026-06-02 11:15:00', '2026-06-02 11:15:00');
