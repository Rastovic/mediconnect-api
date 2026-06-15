-- V23: Telemedicine sessions for Module E (Doctor View Redesign Phase 5).
--
-- `join_token` is intentionally a short low-entropy string and is also
-- spliced into the room URL as ?token=<plaintext>. Token is leaked via
-- Referer, browser history, server access logs, screenshots.
--
-- `reason_for_visit` ends up in the public iCal feed SUMMARY field as PHI.

CREATE TABLE telemedicine_sessions (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id        BIGINT,
    doctor_id         BIGINT,
    room_url          VARCHAR(1024) NOT NULL,
    join_token        VARCHAR(128)  NOT NULL,
    reason_for_visit  VARCHAR(500),
    status            VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
    scheduled_at      DATETIME,
    started_at        DATETIME,
    ended_at          DATETIME,
    recording_url     VARCHAR(2048),
    recording_path    VARCHAR(1000),
    note              TEXT,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_telemedicine_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE SET NULL,
    CONSTRAINT fk_telemedicine_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_telemedicine_doctor ON telemedicine_sessions (doctor_id);
CREATE INDEX idx_telemedicine_status ON telemedicine_sessions (status);

-- Seed two sessions so the page lights up out of the box, including a
-- PHI-bearing reason that lands in the iCal feed.
INSERT INTO telemedicine_sessions (patient_id, doctor_id, room_url, join_token, reason_for_visit, status, scheduled_at, created_at)
VALUES
  (1, 1, '/doctor/telemedicine/rooms/session-1?token=ROOM-SECRET-AAA', 'ROOM-SECRET-AAA',
        'Follow-up: HIV-positive status review', 'OPEN', '2026-06-15 10:00:00', '2026-06-12 09:00:00'),
  (2, 1, '/doctor/telemedicine/rooms/session-2?token=ROOM-SECRET-BBB', 'ROOM-SECRET-BBB',
        'Mental-health intake: bipolar II', 'OPEN', '2026-06-16 11:00:00', '2026-06-12 09:05:00');
