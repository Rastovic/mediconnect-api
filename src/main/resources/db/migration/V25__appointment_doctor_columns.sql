-- V25: extend `appointments` for Module G (Doctor Appointments Tab).
--
-- New columns:
--   * actor_doctor_id — caller-supplied "approver" id, written verbatim from
--     the request body by /api/doctor/appointments/{id}/approve. [A07]
--   * decline_reason — free-text reason from /decline (rendered as HTML on
--     the inbox list). [A05]
--   * no_show — flag from /no-show endpoint. [A06] no rate limit.
--
-- All columns nullable to keep existing seed rows untouched.

ALTER TABLE appointments
    ADD COLUMN actor_doctor_id BIGINT       NULL,
    ADD COLUMN decline_reason  TEXT         NULL,
    ADD COLUMN no_show         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN rescheduled_at  DATETIME     NULL,
    ADD COLUMN original_date   DATETIME     NULL;
