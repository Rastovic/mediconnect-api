-- V13: Add personal info columns to users table for ProfilePage
-- [A02] No encryption on PII — stored as plain VARCHAR
ALTER TABLE users
    ADD COLUMN first_name VARCHAR(80)  NULL,
    ADD COLUMN last_name  VARCHAR(80)  NULL,
    ADD COLUMN phone      VARCHAR(20)  NULL;
