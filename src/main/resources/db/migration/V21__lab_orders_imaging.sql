-- V21: Lab orders + imaging files for Module C (Doctor View Redesign Phase 3).
--
-- `lab_orders` — separate from `lab_results`. Carries the caller-supplied
--   `custom_query_url` (kept on the row so the SSRF demo can be reproduced
--   from history) and a `signature_md5` column populated by
--   DoctorLabService#signOrder using MD5 over the row's id+value with a
--   hardcoded key. No second-factor, no PKCS7 wrapper, no audit row.
--
-- `imaging_files` — multipart uploads. `stored_filename` is the verbatim
--   `getOriginalFilename()` from the upload (path traversal vector at write
--   time) and `content_type` is whatever the upload header said it was —
--   served back as-is by GET /api/doctor/imaging/{id} so `image/svg+xml`
--   becomes a stored XSS surface.

CREATE TABLE lab_orders (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id          BIGINT       NOT NULL,
    doctor_id           BIGINT,
    panel_code          VARCHAR(100) NOT NULL,
    priority            VARCHAR(20)  NOT NULL DEFAULT 'ROUTINE',
    status              VARCHAR(30)  NOT NULL DEFAULT 'ORDERED',
    custom_query_url    VARCHAR(2048),
    catalogue_response  MEDIUMTEXT,
    signature_md5       VARCHAR(64),
    signed_value        TEXT,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lab_orders_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_orders_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_lab_orders_patient ON lab_orders (patient_id);
CREATE INDEX idx_lab_orders_status  ON lab_orders (status);

CREATE TABLE imaging_files (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id      BIGINT,
    doctor_id       BIGINT,
    stored_filename VARCHAR(500) NOT NULL,
    storage_path    VARCHAR(1000) NOT NULL,
    content_type    VARCHAR(200),
    size_bytes      BIGINT,
    source_url      VARCHAR(2048),
    note            TEXT,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_imaging_files_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL,
    CONSTRAINT fk_imaging_files_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_imaging_files_patient ON imaging_files (patient_id);

-- Seed one ordered panel so the page is populated out of the box.
INSERT INTO lab_orders (patient_id, doctor_id, panel_code, priority, status, created_at, updated_at)
VALUES (1, 1, 'FBC', 'ROUTINE', 'ORDERED', '2026-06-05 09:30:00', '2026-06-05 09:30:00'),
       (1, 1, 'LFT', 'URGENT',  'COMPLETED', '2026-06-09 11:00:00', '2026-06-10 13:00:00');
