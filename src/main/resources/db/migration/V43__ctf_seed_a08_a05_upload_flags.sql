-- V43__ctf_seed_a08_a05_upload_flags.sql
-- A08 signing/integrity + A05 upload CTF flags, awarded via GET /api/ctf/behavior/{slug}. Idempotent.
--   #19  a08-jwt-algorithm-confusion            : co-sign a token whose header claims an asymmetric alg (RS*/ES*/PS*).
--   #225 a08-weak-signing-md5-hardcoded-key     : POST /api/doctor/prescriptions/{id}/verify-signature with a recomputed MD5.
--   #50  a08-missing-content-hash-unsigned-artifact : re-upload (replace) a medical-record attachment; no content_hash check.
--   #46  a05-unrestricted-file-upload           : upload an attachment with a disallowed executable extension.
--   #47  a05-path-traversal-write               : upload with a traversal filename that escapes the upload dir.

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a08-jwt-algorithm-confusion','flag{a08_jwt_algorithm_confusion_25c699}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a08-jwt-algorithm-confusion');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a08-weak-signing-md5-hardcoded-key','flag{a08_weak_signing_md5_hardcoded_key_890733}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a08-weak-signing-md5-hardcoded-key');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a08-missing-content-hash-unsigned-artifact','flag{a08_missing_content_hash_unsigned_artifact_137e88}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a08-missing-content-hash-unsigned-artifact');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a05-unrestricted-file-upload','flag{a05_unrestricted_file_upload_16574b}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a05-unrestricted-file-upload');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a05-path-traversal-write','flag{a05_path_traversal_write_827484}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a05-path-traversal-write');
