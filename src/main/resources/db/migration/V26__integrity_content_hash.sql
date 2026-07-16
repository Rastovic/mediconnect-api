-- A08 integrity: store a SHA-256 of every uploaded attachment so tampering is detectable.
ALTER TABLE medical_records ADD COLUMN content_hash VARCHAR(64) NULL;
ALTER TABLE lab_results     ADD COLUMN content_hash VARCHAR(64) NULL;
