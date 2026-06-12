-- V19: Add license verification + uploaded document path to doctors.
--
-- [A04] Required so /api/admin/doctors/verify-license has somewhere to write
--        the (client-claimed) verified=true flag, and /api/admin/staff/onboard
--        has somewhere to record the path of the uploaded license document.
--
-- Both columns are nullable so existing seeded doctors (license_verified=NULL)
-- read as "not verified yet" without breaking the seed migrations.

ALTER TABLE doctors
    ADD COLUMN license_verified      BOOLEAN      DEFAULT FALSE,
    ADD COLUMN license_document_path VARCHAR(500) NULL;
