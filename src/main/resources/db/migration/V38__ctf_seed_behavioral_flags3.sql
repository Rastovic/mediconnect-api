-- V38__ctf_seed_behavioral_flags3.sql
-- Behavioral CTF flags (plan §12.1) awarded via GET /api/ctf/behavior/{slug}
-- once the vulnerable endpoint calls CtfBehaviorRegistry.mark(slug). Idempotent.
--
--   #201 a09-silent-no-audit-on-high-risk-op : POST /api/doctor/patients/{id}/handoff
--        issues a handoff token with no audit_logs entry (the absent record is the finding).
--   #254 a10-silent-null-on-failed-decode    : POST /api/doctor/referrals with a
--        non-empty bundlePayload that is not a valid serialized ReferralBundle, then
--        GET /api/doctor/referrals/inbox triggers decodeBundle() which swallows the
--        failure and returns null.

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a09-silent-no-audit-on-high-risk-op', 'flag{a09_silent_no_audit_on_high_risk_op_2a0fbb}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a09-silent-no-audit-on-high-risk-op');

INSERT INTO ctf_secret (label, flag, status_ok)
SELECT 'a10-silent-null-on-failed-decode', 'flag{a10_silent_null_on_failed_decode_f31aaa}', 'COMPLETED'
WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label = 'a10-silent-null-on-failed-decode');
