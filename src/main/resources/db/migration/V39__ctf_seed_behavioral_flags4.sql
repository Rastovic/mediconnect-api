-- V39__ctf_seed_behavioral_flags4.sql
-- Behavioral CTF flags (plan §12.1), awarded via GET /api/ctf/behavior/{slug}
-- once the vulnerable endpoint calls CtfBehaviorRegistry.mark(slug). Idempotent.
--
--   #38  a01-privilege-escalation-role-mass-assignment : PUT /api/users/{id}/role {"role":"ADMIN"}
--   #56  a01-identity-spoofing-via-request-body        : POST /api/messages with a body senderId != your JWT id
--   #151 a01-auth-context-takeover-impersonation       : POST /api/admin/users/{id}/impersonate
--   #65  a09-wipe-the-audit-trail                       : POST /api/admin/logs/clear
--   #160 a09-selective-log-tampering                    : DELETE /api/admin/logs/{id}
--   #128 a10-fail-open-promote-on-exception             : POST /api/refills with quantity:null (validator throws -> READY)

INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a01-privilege-escalation-role-mass-assignment','flag{a01_privilege_escalation_role_mass_assignment_d4ba63}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a01-privilege-escalation-role-mass-assignment');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a01-identity-spoofing-via-request-body','flag{a01_identity_spoofing_via_request_body_86a283}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a01-identity-spoofing-via-request-body');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a01-auth-context-takeover-impersonation','flag{a01_auth_context_takeover_impersonation_ac2dc1}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a01-auth-context-takeover-impersonation');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a09-wipe-the-audit-trail','flag{a09_wipe_the_audit_trail_854c15}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a09-wipe-the-audit-trail');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a09-selective-log-tampering','flag{a09_selective_log_tampering_a36ec7}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a09-selective-log-tampering');
INSERT INTO ctf_secret (label, flag, status_ok) SELECT 'a10-fail-open-promote-on-exception','flag{a10_fail_open_promote_on_exception_486016}','COMPLETED' WHERE NOT EXISTS (SELECT 1 FROM ctf_secret WHERE label='a10-fail-open-promote-on-exception');
