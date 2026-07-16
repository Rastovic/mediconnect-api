-- V31__ctf_fix_secret_collation.sql
-- V30 created ctf_secret with the utf8mb4_0900_ai_ci default, which does not
-- match the app tables (utf8mb4_unicode_ci). A UNION between them raised
-- "Illegal mix of collations" (MySQL 1271), forcing students into CONVERT/COLLATE
-- gymnastics. Recreate it with the app collation and store a valid status value
-- so the intended UNION needs no collation-mismatched string literals:
--   GET /api/lab-results/search?status=' UNION SELECT
--     NULL,NULL,NULL,flag,NULL,NULL,NULL,status_ok,NULL,NULL,NULL,NULL
--     FROM ctf_secret --
-- (col 4 test_name = flag, col 8 status = a valid enum from the table.)

DROP TABLE IF EXISTS ctf_secret;

CREATE TABLE ctf_secret (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    label     VARCHAR(64)  NOT NULL,
    flag      VARCHAR(128) NOT NULL,
    status_ok VARCHAR(16)  NOT NULL DEFAULT 'COMPLETED',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ctf_secret (label, flag, status_ok)
VALUES ('a05-sql-injection-union', 'flag{a05_sql_injection_union_a8d8bf}', 'COMPLETED');
