# CTF Flag Placement Map

**PLACED** = intended exploit yields the flag today (verified live). **PENDING** = row + hash seeded (submittable), flag not yet positioned.

Progress: **53 / 53 placed**. Plaintext: `scratchpad/chals.json` + `src/main/resources/ctf-flags/*.txt` + `ctf_secret` + env `CTF_A08_FLAG`. Hashes: `V26`. Placements: `V27+`, `application.yaml`, internal connector, flag-file initializer.

| # | Cat | Slug | Diff | Type | Intended path | Status |
|---|---|---|---|---|---|---|
| 42 | A01 | `a01-idor-read-any-object-by-id` | E | retrieval | GET /api/appointments/{id} with an id that isn't yours | **PLACED** |
| 94 | A01 | `a01-idor-via-query-param` | M | retrieval | GET /api/messages/conversations?userId= — userId not v | **PLACED** |
| 93 | A01 | `a01-function-level-access-returns-all` | E | retrieval | GET /api/medical-records returns all rows regardless o | **PLACED** |
| 191 | A01 | `a01-permitall-on-protected-routes` | M | retrieval | All seven /api/doctor/** controllers are permitAll() ; | **PLACED** |
| 38 | A01 | `a01-privilege-escalation-role-mass-assignment` | E | retrieval | PUT /api/users/{id}/role takes the role from the body  | **PLACED** |
| 56 | A01 | `a01-identity-spoofing-via-request-body` | M | retrieval | POST /api/messages trusts senderId from the body → sen | **PLACED** |
| 151 | A01 | `a01-auth-context-takeover-impersonation` | H | retrieval | POST /api/admin/users/{id}/impersonate issues a JWT fo | **PLACED** |
| 173 | A01 | `a01-workflow-bypass-force-dispense` | H | retrieval | AdminClinicalService#forceDispense bypasses queue + va | **PLACED** |
| 28 | A02 | `a02-blanket-securityconfig-weakening` | E | retrieval | Cluster #28/#30/#31/#22 — demonstrate a cross-origin o | **PLACED** |
| 32 | A02 | `a02-nooppasswordencoder` | M | retrieval | The registered NoOpPasswordEncoder compares passwords  | **PLACED** |
| 64 | A02 | `a02-config-secrets-dump` | M | retrieval | GET /api/admin/config returns the raw Environment (als | **PLACED** |
| 169 | A02 | `a02-destructive-ops-endpoint-system-exit` | H | behavioral | AdminOpsService#restart calls System.exit(0) ; reachin | **PLACED** |
| 206 | A03 | `a03-xxe-unhardened-xml-parser` | M | retrieval | DoctorNoteService#importXml uses default DocumentBuild | **PLACED** |
| 204 | A03 | `a03-ssti-freemarker-inline-eval` | M | retrieval | TemplateRenderer#renderInline renders attacker input a | **PLACED** |
| 205 | A03 | `a03-ssti-template-path-traversal` | H | retrieval | TemplateRenderer#renderByName resolves a caller-chosen | **PLACED** |
| 229 | A03 | `a03-nashorn-eval-rce-deprecated-engine` | H | retrieval | DoctorPrescribingService#drugInteractions eval() s a f | **PLACED** |
| 20 | A04 | `a04-md5-unsalted-passwords` | E | retrieval | PasswordUtils stores unsalted MD5; crack a leaked hash | **PLACED** |
| 11 | A04 | `a04-passwordhash-exposed-in-responses` | E | retrieval | User / UserDto serialize passwordHash ; read it from a | **PLACED** |
| 16 | A04 | `a04-hardcoded-jwt-signing-secret` | M | retrieval | Secret is hardcoded ( mediconnect-super-secret-2024 ); | **PLACED** |
| 7 | A04 | `a04-plaintext-pii-at-rest` | E | retrieval | PII columns are plaintext and exposed unmasked in DTOs | **PLACED** |
| 153 | A04 | `a04-predictable-rng-on-reset` | H | retrieval | generateRandomPassword uses java.util.Random ; predict | **PLACED** |
| 51 | A05 | `a05-sql-injection-union` | M | retrieval | GET /api/lab-results/search concatenates 4 params into | **PLACED** |
| 194 | A05 | `a05-blind-boolean-sqli-multi-clause` | H | retrieval | DoctorRosterService#listPatients concatenates q / acti | **PLACED** |
| 55 | A05 | `a05-stored-xss-admin-bot` | M | retrieval | POST /api/messages stores raw HTML; a simulated admin  | **PLACED** |
| 170 | A05 | `a05-command-injection` | H | retrieval | POST /api/admin/ops/backup?dbName= passes the param to | **PLACED** |
| 48 | A05 | `a05-path-traversal-read` | E | retrieval | MedicalRecordService uses a caller filePath unsanitize | **PLACED** |
| 47 | A05 | `a05-path-traversal-write` | M | retrieval | Upload uses getOriginalFilename() verbatim; a traversa | **PLACED** |
| 46 | A05 | `a05-unrestricted-file-upload` | E | retrieval | Attachment endpoint enforces no type/size; upload an e | **PLACED** |
| 264 | A05 | `a05-csv-formula-injection` | M | retrieval | DoctorAppointmentService#exportCsv writes note cells u | **PLACED** |
| 59 | A06 | `a06-missing-state-machine-dispense` | M | behavioral | PrescriptionService allows any status transition; repl | **PLACED** |
| 43 | A06 | `a06-missing-state-machine-appointment` | E | behavioral | PUT /api/appointments/{id}/status accepts any transiti | **PLACED** |
| 187 | A06 | `a06-unbounded-fan-out` | M | behavioral | Broadcast has no recipient/size cap; a single call fan | **PLACED** |
| 25 | A07 | `a07-user-enumeration` | E | retrieval | Distinct error messages on register/login reveal which | **PLACED** |
| 27 | A07 | `a07-no-login-rate-limiting` | M | retrieval | /api/auth/login has no throttle/lockout; brute the enu | **PLACED** |
| 33 | A07 | `a07-expiry-skip-path-bypass` | H | retrieval | SKIP_EXPIRY_PATHS accepts expired tokens on /api/publi | **PLACED** |
| 34 | A07 | `a07-fail-open-token-validation` | H | retrieval | Token-parse exceptions are swallowed and the request p | **PLACED** |
| 245 | A08 | `a08-java-deserialization-rce` | H | retrieval | The referral inbox base64-deserializes a ReferralBundl | **PLACED** |
| 210 | A08 | `a08-alg-none-jwt` | M | retrieval | JwtNoneVerifier accepts alg:none ; co-sign as another  | **PLACED** |
| 19 | A08 | `a08-jwt-algorithm-confusion` | H | retrieval | Verifier can be tricked into using the public key as a | **PLACED** |
| 225 | A08 | `a08-weak-signing-md5-hardcoded-key` | M | retrieval | PrescriptionSigner signs with MD5 + a hardcoded key; r | **PLACED** |
| 50 | A08 | `a08-missing-content-hash-unsigned-artifact` | E | retrieval | Uploads/PDFs carry no content_hash or signature; repla | **PLACED** |
| 177 | A08 | `a08-clinical-value-tamper-no-amend` | M | retrieval | overrideLabResultValue mutates a result with no amend  | **PLACED** |
| 188 | A08 | `a08-overwrite-in-place-redact` | M | behavioral | AdminBroadcastService#redact overwrites message conten | **PLACED** |
| 65 | A09 | `a09-wipe-the-audit-trail` | E | retrieval | Perform a seeded logged action, then POST /api/admin/l | **PLACED** |
| 160 | A09 | `a09-selective-log-tampering` | M | retrieval | DELETE /api/admin/logs/{id} removes a single record —  | **PLACED** |
| 201 | A09 | `a09-silent-no-audit-on-high-risk-op` | M | behavioral | Handoff issuance writes no audit entry; the absent log | **PLACED** |
| 220 | A10 | `a10-ssrf-server-fetches-your-url` | E | retrieval | DoctorLabService#importUrl fetches a caller URL with n | **PLACED** |
| 228 | A10 | `a10-blind-ssrf-via-outbound-post` | H | retrieval | Prescription flow POSTs to a caller pharmacyCallbackUr | **PLACED** |
| 128 | A10 | `a10-fail-open-promote-on-exception` | M | retrieval | RefillQueueService promotes to READY on any validator  | **PLACED** |
| 131 | A10 | `a10-toctou-race-double-dispense` | H | behavioral | dispense checks then acts without locking; fire concur | **PLACED** |
| 130 | A10 | `a10-swallow-all-in-background-worker` | M | behavioral | @Scheduled runWorker catches every exception silently; | **PLACED** |
| 136 | A10 | `a10-verbose-exception-leakage` | E | retrieval | GET /api/refills returns raw exception text in failure | **PLACED** |
| 254 | A10 | `a10-silent-null-on-failed-decode` | M | behavioral | decodeBundle returns null on failure instead of errori | **PLACED** |

## Placed so far (verified)
- `a01-function-level-access-returns-all` (#93): medical_records.diagnosis via GET /api/medical-records (V27)
- `a01-idor-read-any-object-by-id` (#42): appointments.notes (patient 2) via GET /api/appointments/patient/{id} (V28)
- `a01-idor-via-query-param` (#94): messages.content via GET /api/messages/conversations?userId={id} (V30)
- `a05-sql-injection-union` (#51): ctf_secret via UNION SQLi on GET /api/lab-results/search (V30/V31)
- `a02-config-secrets-dump` (#64): seeded property via GET /api/admin/config + /actuator/env
- `a05-path-traversal-read` (#48): classpath ctf-flags/ file on disk, read via lab-results download
- `a01-permitall-on-protected-routes` (#191): clinical_notes.rendered_html via GET /api/doctor/notes (V32)
- `a04-plaintext-pii-at-rest` (#7): patients.insurance_number (patient 2) via GET /api/patients/by-user/4 (V33)
- `a04-hardcoded-jwt-signing-secret` (#16): ctf_secret via admin-gated GET /api/ctf/admin-secret (V33)
- `a10-ssrf-server-fetches-your-url` (#220): ctf_secret via /internal/metadata on unpublished connector 8099, reflected SSRF (V34)
- `a05-command-injection` (#170): ctf-flags/a05-command-injection.txt on disk, read via shell RCE
- `a03-nashorn-eval-rce-deprecated-engine` (#229): ctf-flags/a03-nashorn-eval-rce.txt on disk, via SSRF->Nashorn RCE chain
- `a08-java-deserialization-rce` (#245): env var CTF_A08_FLAG (RCE-only), exfil via Java deserialization RCE
- `a05-stored-xss-admin-bot` (#55): ctf_secret via server-side detector GET /api/ctf/xss/check (V35)
- `a02-destructive-ops-endpoint-system-exit` (#169): ctf_secret via behavioral registry; System.exit defused (V36)
- `a06-missing-state-machine-appointment` (#43): ctf_secret via behavioral registry; illegal transition detected in updateStatus (V36)
- `a06-missing-state-machine-dispense` (#59): ctf_secret via behavioral registry; double-dispense detected (V37)
- `a10-toctou-race-double-dispense` (#131): ctf_secret via behavioral registry; race counter in refill dispense (V37)
- `a06-unbounded-fan-out` (#187): ctf_secret via behavioral registry; fan-out detected in broadcast (V37)
- `a08-overwrite-in-place-redact` (#188): ctf_secret via behavioral registry; in-place overwrite detected in redact (V37)
- `a09-silent-no-audit-on-high-risk-op` (#201): ctf_secret via behavioral registry; mark on handoff issuance (V38)
- `a10-silent-null-on-failed-decode` (#254): ctf_secret via behavioral registry; mark in decodeBundle silent-null catch (V38)
- `a01-privilege-escalation-role-mass-assignment` (#38): ctf_secret via behavioral registry; mark on role->ADMIN (V39)
- `a01-identity-spoofing-via-request-body` (#56): ctf_secret via behavioral registry; senderId!=JWT detected in MessageService (V39)
- `a01-auth-context-takeover-impersonation` (#151): ctf_secret via behavioral registry; mark on impersonate (V39)
- `a09-wipe-the-audit-trail` (#65): ctf_secret via behavioral registry; mark on logs/clear (V39)
- `a09-selective-log-tampering` (#160): ctf_secret via behavioral registry; mark on logs/{id} delete (V39)
- `a10-fail-open-promote-on-exception` (#128): ctf_secret via behavioral registry; mark in fail-open catch (V39)
- `a08-alg-none-jwt` (#210): ctf_secret via behavioral registry; alg:none header detected in coSign (V40)
- `a01-workflow-bypass-force-dispense` (#173): ctf_secret via behavioral registry; mark in forceDispense (V40)
- `a08-clinical-value-tamper-no-amend` (#177): ctf_secret via behavioral registry; mark in overrideLabResultValue (V40)
- `a03-xxe-unhardened-xml-parser` (#206): ctf-flags/a03-xxe-*.txt read via XXE, reflected in renderedHtml
- `a03-ssti-freemarker-inline-eval` (#204): ctf-flags/a03-ssti-*.txt read via Freemarker SSTI, reflected
- `a03-ssti-template-path-traversal` (#205): seeded .ftl in notes/templates/ loaded by name (no allow-list)
- `a04-passwordhash-exposed-in-responses` (#11): users.password_hash of seeded 'records_svc' via GET /api/users (V41)
- `a10-verbose-exception-leakage` (#136): refill_requests.failure_reason of a seeded FAILED refill via GET /api/refills (V41)
- `a07-user-enumeration` (#25): behavioral: distinct login error confirms account exists; mark in AuthService.login (V42)
- `a07-no-login-rate-limiting` (#27): behavioral: >=5 failed logins with no lockout; bump/mark in AuthService.login (V42)
- `a07-expiry-skip-path-bypass` (#33): behavioral: expired token accepted on skip-expiry path; mark in JwtAuthenticationFilter (V42)
- `a07-fail-open-token-validation` (#34): behavioral: malformed token swallowed (fail-open); mark in JwtAuthenticationFilter catch (V42)
- `a08-jwt-algorithm-confusion` (#19): behavioral: co-sign accepts a token whose header claims RS*/ES*/PS*; mark in DoctorPrescribingService.coSign (V43)
- `a08-weak-signing-md5-hardcoded-key` (#225): behavioral: recomputed MD5 signature over tampered content accepted; mark in DoctorPrescribingService.verifySignature (V43)
- `a08-missing-content-hash-unsigned-artifact` (#50): behavioral: re-upload replaces a record attachment with no content_hash check; mark in MedicalRecordService.uploadAttachment (V43)
- `a05-unrestricted-file-upload` (#46): behavioral: attachment upload accepts a disallowed executable extension; mark in MedicalRecordService.uploadAttachment (V43)
- `a05-path-traversal-write` (#47): behavioral: traversal filename escapes the upload dir; mark in MedicalRecordService.uploadAttachment (V43)
- `a05-csv-formula-injection` (#264): behavioral: export emits a =/+/-/@-prefixed cell verbatim; mark in DoctorAppointmentService.exportCsv (V44)
- `a05-blind-boolean-sqli-multi-clause` (#194): ctf_secret.flag boolean-extracted via GET /api/doctor/patients?q= (DoctorRosterService#listPatients) (V45)
- `a04-md5-unsalted-passwords` (#20): behavioral: log in as seeded 'crackme' (MD5 of 'sunshine'); mark in AuthService.login (V46)
- `a04-predictable-rng-on-reset` (#153): behavioral: log in with the predictable-RNG reset password; mark in AuthService.login (V46)
- `a02-blanket-securityconfig-weakening` (#28): behavioral: cross-origin state-changing request accepted (CorsProbeFilter); mark on foreign Origin (V46)
- `a02-nooppasswordencoder` (#32): behavioral: authenticate by replaying the leaked password_hash (NoOp plaintext compare); mark in AuthService.login (V46)
- `a10-blind-ssrf-via-outbound-post` (#228): outbound POST header X-Internal-Signing-Token=flag on pharmacyCallbackUrl; mark in DoctorPrescribingService.sendPharmacyNotice (V47)
- `a10-swallow-all-in-background-worker` (#130): behavioral: poison refill (requestedBy=-999) makes the @Scheduled tick throw; swallowed by the generic catch; mark in RefillQueueService.runWorker (V47)
