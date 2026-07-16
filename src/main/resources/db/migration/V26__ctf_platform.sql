-- V26__ctf_platform.sql
-- CTF learning-platform layer over the vulnerable MediConnect app.
-- Honor-based (OWASP Juice Shop model): flag hashes are salted sha256, NOT a
-- security boundary. See CTF_PLATFORM_PLAN.md §5.
-- NOTE: this migration is the CTF instrumentation and lives only on ctf-platform.

CREATE TABLE ctf_challenge (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    slug          VARCHAR(80)  NOT NULL,
    owasp_category VARCHAR(4)  NOT NULL,
    title         VARCHAR(160) NOT NULL,
    difficulty    ENUM('EASY','MEDIUM','HARD') NOT NULL,
    points        INT          NOT NULL,
    finding_id    INT          NULL,
    summary       VARCHAR(512) NULL,
    objective     VARCHAR(512) NULL,
    target_hint   VARCHAR(512) NULL,
    intended_path TEXT         NULL,
    flag_hash     CHAR(64)     NOT NULL,
    flag_salt     VARCHAR(32)  NOT NULL,
    is_behavioral BOOLEAN      NOT NULL DEFAULT FALSE,
    is_core       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ctf_challenge_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ctf_progress (
    id           BIGINT    NOT NULL AUTO_INCREMENT,
    user_id      BIGINT    NOT NULL,
    challenge_id BIGINT    NOT NULL,
    solved       BOOLEAN   NOT NULL DEFAULT FALSE,
    solved_at    DATETIME  NULL,
    attempts     INT       NOT NULL DEFAULT 0,
    hints_used   INT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ctf_progress_user_challenge (user_id, challenge_id),
    CONSTRAINT fk_ctf_progress_challenge FOREIGN KEY (challenge_id) REFERENCES ctf_challenge (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ctf_settings (
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(512) NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ctf_submission (
    id                  BIGINT    NOT NULL AUTO_INCREMENT,
    user_id             BIGINT    NOT NULL,
    challenge_id        BIGINT    NOT NULL,
    submitted_flag_hash CHAR(64)  NULL,
    correct             BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at          DATETIME  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ctf_submission_challenge FOREIGN KEY (challenge_id) REFERENCES ctf_challenge (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Instructor gating. Default: gating OFF => every category open (plan §1 default).
INSERT INTO ctf_settings (setting_key, setting_value) VALUES
  ('gating_enabled', 'false'),
  ('open_categories', 'A01,A02,A03,A04,A05,A06,A07,A08,A09,A10'),
  ('force_open_slugs', ''),
  ('force_locked_slugs', '');

-- 53 challenge rows (curation §6.1). flag_hash = sha256(salt || flag).
INSERT INTO ctf_challenge
 (slug, owasp_category, title, difficulty, points, finding_id, objective, target_hint, intended_path, flag_hash, flag_salt, is_behavioral, is_core, sort_order) VALUES
('a01-idor-read-any-object-by-id','A01','IDOR — read any object by id','EASY',100,42,'Read another user''s appointment/record by guessing the id.','GET /api/appointments/{id} with an id that isn''t yours — no ownership check; flag in the returned object.','AppointmentController GET /{id}','435635145fb774803c08f9c104fe4a9970ccea754912c05b57fd6805f52783aa','6d9ee18597d90621',FALSE,TRUE,1),
('a01-idor-via-query-param','A01','IDOR via query param','MEDIUM',200,94,'Read another user''s conversations by tampering a query param.','GET /api/messages/conversations?userId= — userId not verified against the JWT subject.','MessageController','f86e60bc1b53ecf9574f9490dd94e934e4dfe60646affed00eb223dd5b77e1a7','5bdee13a10f9a89b',FALSE,TRUE,2),
('a01-function-level-access-returns-all','A01','Function-level access — returns-all','EASY',100,93,'List every patient''s medical records without a role.','GET /api/medical-records returns all rows regardless of caller role.','MedicalRecordController','47b1792574f7916eb0671ffeec0a8c7bc6d4bd037218635539d9fbb6cd7c518f','46e3a55b92d2359f',FALSE,TRUE,3),
('a01-permitall-on-protected-routes','A01','permitAll on protected routes','MEDIUM',200,191,'Hit doctor-only endpoints as a patient.','All seven /api/doctor/** controllers are permitAll() ; call one directly; flag behind it.','SecurityConfig','d2f64a342920f88877432def56eab2ebc64612341f7b141e13a4884e668b0755','c0f369bb5d68b5ed',FALSE,TRUE,4),
('a01-privilege-escalation-role-mass-assignment','A01','Privilege escalation — role mass-assignment','EASY',100,38,'Set your own role via an update call.','PUT /api/users/{id}/role takes the role from the body with no authority check → become ADMIN → read admin flag.','UserController','ac5067e24a5ee1355be6c779d770e4e54a17d4b9cbc0c3181baaab9754a9c377','d17dfecd4b2ecc24',FALSE,TRUE,5),
('a01-identity-spoofing-via-request-body','A01','Identity spoofing via request body','MEDIUM',200,56,'Act as another user by supplying their id.','POST /api/messages trusts senderId from the body → send/read as someone else.','MessageService','f1b8d002cc251482fc6c0e6ebbef09499ce1142b9dc880ba29d09c010f7dbacd','3d1aa186e4aa6d25',FALSE,TRUE,6),
('a01-auth-context-takeover-impersonation','A01','Auth-context takeover — impersonation','HARD',300,151,'Mint a valid session for any user.','POST /api/admin/users/{id}/impersonate issues a JWT for that user → use it → read admin flag.','AdminUserService#impersonate','bbf3ffa8badc67f82a9c7eab27441803aeadb3309580cd98abbc48f7a5259a81','11e8ed31440b9b65',FALSE,TRUE,7),
('a01-workflow-bypass-force-dispense','A01','Workflow bypass — force dispense','HARD',300,173,'Dispense a prescription skipping the pharmacist workflow entirely.','AdminClinicalService#forceDispense bypasses queue + validator + role → illegitimate dispense yields flag.','AdminClinicalService#forceDispense','ac9b37349a4477e0f31c155d39e4c331f3af850675e12cf75b19300b3f5e8be8','e069605bbac39293',FALSE,TRUE,8),
('a02-blanket-securityconfig-weakening','A02','Blanket SecurityConfig weakening','EASY',100,28,'Exploit disabled CSRF / wildcard CORS / dropped security headers / all-endpoints-open.','Cluster #28/#30/#31/#22 — demonstrate a cross-origin or header-based attack that the missing defenses allow; flag on success.','SecurityConfig','3b9d9201e986b997f44f16bacd687521ab001c340a75f0a997d71b935a5b865c','f22769d9e2bac55c',FALSE,TRUE,9),
('a02-nooppasswordencoder','A02','NoOpPasswordEncoder','MEDIUM',200,32,'Exploit plaintext-comparison password handling.','The registered NoOpPasswordEncoder compares passwords as plaintext; combine with a hash/DB leak to authenticate → flag.','SecurityConfig','6f286291f455eb72a999daac3fb84e862ba3a120d8018044cf2d925bd43c9468','ec4461e97bb8c5fb',FALSE,TRUE,10),
('a02-config-secrets-dump','A02','Config / secrets dump','MEDIUM',200,64,'Read raw server configuration incl. secrets.','GET /api/admin/config returns the raw Environment (also Actuator #4); flag is a seeded property.','AdminService /admin/config','2fb1a528a3bef5bffa8a65a2e291ff838feaf12563d6551a3774988a4629dde9','301c48d93c13bc3f',FALSE,TRUE,11),
('a02-destructive-ops-endpoint-system-exit','A02','Destructive ops endpoint (System.exit)','HARD',300,169,'Trigger a denial-of-service by design.','AdminOpsService#restart calls System.exit(0) ; reaching it (via permitAll) kills the app. Flag awarded for demonstrating the exposed control, then fixed for stability.','AdminOpsService#restart','a1de645a3c403be234a5e16478dc606617ec0a12f8e139e4dea5a29dbb2fca58','65e9c3a111b6b64c',TRUE,TRUE,12),
('a03-xxe-unhardened-xml-parser','A03','XXE — unhardened XML parser','MEDIUM',200,206,'Read a local file via an XML external entity.','DoctorNoteService#importXml uses default DocumentBuilderFactory ; submit XML with an external entity pointing at the flag file.','DoctorNoteService#importXml','572ddb70476a61c879227cb68d92cb2511cc155197b129687f413acf10b0246a','7b1c75b0c8d2a1cb',FALSE,TRUE,13),
('a03-ssti-freemarker-inline-eval','A03','SSTI — Freemarker inline eval','MEDIUM',200,204,'Evaluate a server-side template expression.','TemplateRenderer#renderInline renders attacker input as a Freemarker template (via note create) → expression executes → flag.','TemplateRenderer#renderInline','159bf0aa9be09396c5212fa7ee9c170c1e07d1fd8b6131a7da89c9dd09220d26','45d2de9a111e358f',FALSE,TRUE,14),
('a03-ssti-template-path-traversal','A03','SSTI — template path traversal','HARD',300,205,'Select an arbitrary template file by name.','TemplateRenderer#renderByName resolves a caller-chosen name with no allow-list → load an unintended template → flag.','TemplateRenderer#renderByName','cb4614051c30f303b2bf6c920e1d1a5f179949bea9f723c7bca2907f76d52223','a5e9afee713ab96a',FALSE,TRUE,15),
('a03-nashorn-eval-rce-deprecated-engine','A03','Nashorn eval RCE (deprecated engine)','HARD',300,229,'Execute code via the bundled JS engine.','DoctorPrescribingService#drugInteractions eval() s a fetched body (the dangerous nashorn-core:15.6 dep); return JS that reads the flag file.','DoctorPrescribingService#drugInteractions · pom.xml','0369df977a208e5f916e490d8d7f10dfaf6f4ad4070ca6810ad66ad8f1ca18fc','6f5d700969a9e74e',FALSE,TRUE,16),
('a04-md5-unsalted-passwords','A04','MD5 unsalted passwords','EASY',100,20,'Recover a password from its hash.','PasswordUtils stores unsalted MD5; crack a leaked hash and log in as the target → flag.','PasswordUtils','5459dbe53d3dcd7d5cb4e653d5f6ced30f10c41ef0dffd7f21494ad742a3dd60','0b314b75f526d179',FALSE,TRUE,17),
('a04-passwordhash-exposed-in-responses','A04','passwordHash exposed in responses','EASY',100,11,'Harvest a password hash straight from the API.','User / UserDto serialize passwordHash ; read it from any user response, then crack (feeds #20).','User.java / UserDto','fa3d67515464fa9256252229826379d072417a402e6c181d7e486b4e8015fcec','bf311193b63ac13e',FALSE,TRUE,18),
('a04-hardcoded-jwt-signing-secret','A04','Hardcoded JWT signing secret','MEDIUM',200,16,'Forge an admin JWT.','Secret is hardcoded ( mediconnect-super-secret-2024 ); sign a token with role: ADMIN → admin flag.','JwtUtil','da92f84498c1f268534adeb31c0c1f7be78e5600d7980258a3c41655a60fb748','3601e85923882f9d',FALSE,TRUE,19),
('a04-plaintext-pii-at-rest','A04','Plaintext PII at rest','EASY',100,7,'Read sensitive PII stored/returned unencrypted & unmasked.','PII columns are plaintext and exposed unmasked in DTOs; flag hidden in a sensitive field.','V2 migration / DTOs','c0eefe9ba4a30259f9fc089d070597c2f412db03a5e6c22640ea74ab0e522ee5','fa0798995528c301',FALSE,TRUE,20),
('a04-predictable-rng-on-reset','A04','Predictable RNG on reset','HARD',300,153,'Predict a generated reset password.','generateRandomPassword uses java.util.Random ; predict the sequence, hijack the reset, read the flag.','AdminUserService#generateRandomPassword','8952810894792d00952add6fe8ca20c1b0cbf97223e57980b09c1bc8ada7a9a3','ef7575170ecb5ddd',FALSE,TRUE,21),
('a05-sql-injection-union','A05','SQL injection (UNION)','MEDIUM',200,51,'Dump a hidden flag row via SQLi.','GET /api/lab-results/search concatenates 4 params into SQL; inject UNION SELECT to pull the flag.','LabResultService#search','bec5b65c50b8b24afd784ac7b1255fbc21bb9c7fcd9c18c18a1efe4cdb6d4282','5dea56d21e902437',FALSE,TRUE,22),
('a05-blind-boolean-sqli-multi-clause','A05','Blind/boolean SQLi (multi-clause)','HARD',300,194,'Extract data with no direct output.','DoctorRosterService#listPatients concatenates q / active / recentDays into multiple clauses → boolean-blind extraction of the flag.','DoctorRosterService#listPatients','0d674e59d3abc29ba98c2447c55609b84bb9d72bc5906065ccb175d5d86943ba','39c23d1e05de314b',FALSE,TRUE,23),
('a05-stored-xss-admin-bot','A05','Stored XSS → admin bot','MEDIUM',200,55,'Execute script in an admin context.','POST /api/messages stores raw HTML; a simulated admin victim-bot renders it and the payload posts an admin secret to /api/ctf/xss-callback → flag.','MessageService · victim-bot (to build)','338fd78f365529cb34438847b4c0cbe10a14a506d534ad0800e658f43882bae5','25a62454b225279b',FALSE,TRUE,24),
('a05-command-injection','A05','Command injection','HARD',300,170,'Run an OS command.','POST /api/admin/ops/backup?dbName= passes the param to a shell; inject ; cat /ctf-flags/a05.txt .','AdminOpsService#backup','bff2085115a176fa48920c431fa038fc30d97ba553e4dd1adeb2b9c9928be74a','8837f281e07c78ef',FALSE,TRUE,25),
('a05-path-traversal-read','A05','Path traversal — read','EASY',100,48,'Read an arbitrary file off disk.','MedicalRecordService uses a caller filePath unsanitized; ../ to the flag file.','MedicalRecordService','3700845763b7fdae1291cc1d2a173ff6686edd3122fca051ef40925f9a128a30','988c9c2c42a21c28',FALSE,TRUE,26),
('a05-path-traversal-write','A05','Path traversal — write','MEDIUM',200,47,'Write a file outside the upload dir.','Upload uses getOriginalFilename() verbatim; a traversal filename writes anywhere. Flag confirms placement.','MedicalRecordService','05f717cbf2dad5e493cd1b94313cecd6953a601e207e4320086237397eaeb5ea','818a34ccf03fa9f2',FALSE,TRUE,27),
('a05-unrestricted-file-upload','A05','Unrestricted file upload','EASY',100,46,'Upload a disallowed file type.','Attachment endpoint enforces no type/size; upload an executable/script; flag on acceptance.','MedicalRecordService (POST attachment)','3ed0935f1e1e0d146ca328239ad8cbf46e97532bd44198d4aa0b8f0730afddaa','3359b5f50a521dba',FALSE,TRUE,28),
('a05-csv-formula-injection','A05','CSV formula injection','MEDIUM',200,264,'Inject a spreadsheet formula via export.','DoctorAppointmentService#exportCsv writes note cells unescaped; a = -prefixed payload executes on open. Flag on crafted export.','DoctorAppointmentService#exportCsv','0d9e65acd2ccda789df2a3be9cd57d09e72bfb51a01092c3fc8b85da2cf8ed02','3ebd96f0b31d4ef2',FALSE,TRUE,29),
('a06-missing-state-machine-dispense','A06','Missing state machine — dispense','MEDIUM',200,59,'Double-dispense / dispense a voided prescription.','PrescriptionService allows any status transition; replay dispense → flag on the illegal second dispense.','PrescriptionService','0a41b696e9d9dbf3e9cf3cbb7596e993a794e0ec2b0186658c5b3baedf304fb7','f9ff45cc4bc79f8a',TRUE,TRUE,30),
('a06-missing-state-machine-appointment','A06','Missing state machine — appointment','EASY',100,43,'Move an appointment to an illegal status.','PUT /api/appointments/{id}/status accepts any transition (e.g. completed → pending). Flag on an impossible transition.','AppointmentService','29c046912ca4aa41a42613ec1eb91ffe6ecd21c8d730350357a4e3201e8dbb0a','5ba20b24623af216',TRUE,TRUE,31),
('a06-unbounded-fan-out','A06','Unbounded fan-out','MEDIUM',200,187,'Trigger an unbounded broadcast.','Broadcast has no recipient/size cap; a single call fans out to everyone. Flag on demonstrating the amplification.','AdminBroadcastService#broadcast','10fe57b7ef6842633907119bb4703b427704835e37d94f992dfc26990cdb9aaa','6ebc6dd855ed29c1',TRUE,TRUE,32),
('a07-user-enumeration','A07','User enumeration','EASY',100,25,'Discover valid accounts.','Distinct error messages on register/login reveal which usernames exist; enumerate to the target account (feeds #27).','AuthService','360ca25a7e46ca63ec47eedc776f41982eab7058b088a31837e004c725c76361','82626c388cac34da',FALSE,TRUE,33),
('a07-no-login-rate-limiting','A07','No login rate limiting','MEDIUM',200,27,'Brute-force a weak password.','/api/auth/login has no throttle/lockout; brute the enumerated account → flag on its dashboard.','AuthService#login','383fbe95874b871c97327851995b9f3c49a8ee8b7cf7db998e52e61d2f09abb9','1e3483a8cbb4cd8c',FALSE,TRUE,34),
('a07-expiry-skip-path-bypass','A07','Expiry-skip path bypass','HARD',300,33,'Use an expired token where expiry isn''t checked.','SKIP_EXPIRY_PATHS accepts expired tokens on /api/public|legacy|reports/ ; present one → flag.','JwtAuthenticationFilter','0f361291c405b9539995efe63db30a447d4277a672fa9062298244c3698ac5e2','e5b7cffaf150380d',FALSE,TRUE,35),
('a07-fail-open-token-validation','A07','Fail-open token validation','HARD',300,34,'Authenticate with a malformed/invalid token.','Token-parse exceptions are swallowed and the request proceeds authenticated; craft an invalid token → reach flag.','JwtAuthenticationFilter','625a612674a3cb1282263ec5bd0503372937f1a3aea8554e4c5078ebfaa4ad10','3f8fc73d9a23bb31',FALSE,TRUE,36),
('a08-java-deserialization-rce','A08','Java deserialization RCE','HARD',300,245,'Execute code via a serialized object.','The referral inbox base64-deserializes a ReferralBundle with no allow-list; send a gadget chain that reads the flag file.','DoctorReferralService#decodeBundle','5fd40712510b81fc937cfa48be0bb13c117f52a0020d20117c5fce23a433cd58','c9558fce73426cbd',FALSE,TRUE,37),
('a08-alg-none-jwt','A08','alg:none JWT','MEDIUM',200,210,'Forge an unsigned token the server trusts.','JwtNoneVerifier accepts alg:none ; co-sign as another identity → flag.','JwtNoneVerifier','00d3c0378f7ce89f706369c04ec3ddd4cf3181543b34204ea021089768d22ef5','6c002bce29a7faa4',FALSE,TRUE,38),
('a08-jwt-algorithm-confusion','A08','JWT algorithm confusion','HARD',300,19,'Exploit HS/RS key confusion.','Verifier can be tricked into using the public key as an HMAC secret; sign a forged token accordingly → flag.','JwtUtil','f77fecb2b04d81d0ec11aaf275afcc08ab16f2df1f9d342508372c9dccc39c36','83c74ba260aabd43',FALSE,TRUE,39),
('a08-weak-signing-md5-hardcoded-key','A08','Weak signing (MD5 + hardcoded key)','MEDIUM',200,225,'Forge a valid prescription signature.','PrescriptionSigner signs with MD5 + a hardcoded key; recompute a signature for tampered content → flag.','PrescriptionSigner','a9bdeee9e39a93cff7a26588d79f16c78401a50e74a9e9e9a65acb5b468593a0','299941155b94a4c3',FALSE,TRUE,40),
('a08-missing-content-hash-unsigned-artifact','A08','Missing content hash / unsigned artifact','EASY',100,50,'Swap a file/PDF undetected.','Uploads/PDFs carry no content_hash or signature; replace the artifact and it''s accepted. Flag confirms the integrity gap.','MedicalRecordService (fold #44/#227 PDFs)','e5ead18cff47cdfe787e29df02c8744758665bc7305713f7342f43b749791fcb','6569f9eef83f7d8d',FALSE,TRUE,41),
('a08-clinical-value-tamper-no-amend','A08','Clinical-value tamper (no amend)','MEDIUM',200,177,'Silently alter a lab result.','overrideLabResultValue mutates a result with no amend flag / audit; change it → flag on undetected mutation.','AdminClinicalService#overrideLabResultValue','15a21ca8c191767817b078fceb797eb6540576d711040ef1f2cc9725c422557c','d829b792064117d1',FALSE,TRUE,42),
('a08-overwrite-in-place-redact','A08','Overwrite-in-place (redact)','MEDIUM',200,188,'Destroy original content with no version history.','AdminBroadcastService#redact overwrites message content in place; the lost original is the finding. Flag on irreversible edit.','AdminBroadcastService#redact','181b237bed9c50f8c3f2d75e523f8ec1628e8efe7b47351f04f389750531a836','773dea59e2ea0d8d',TRUE,TRUE,43),
('a09-wipe-the-audit-trail','A09','Wipe the audit trail','EASY',100,65,'Erase all evidence of an action.','Perform a seeded logged action, then POST /api/admin/logs/clear ; the missing record yields the flag.','AdminService /admin/logs/clear','88533bdacf9b79f37088d18594dca501eaf8259648a13aeb4262a2bd4d68fbe6','ac6e022d59f57cea',FALSE,TRUE,44),
('a09-selective-log-tampering','A09','Selective log tampering','MEDIUM',200,160,'Surgically delete one incriminating entry.','DELETE /api/admin/logs/{id} removes a single record — harder to notice than a wipe. Flag on the targeted deletion.','AdminAuditService#deleteOne','f0616c1d763602d7e0ea3adefae2ec37f19742fde34dc665e3f3cf3494523f51','a2578680a6992738',FALSE,TRUE,45),
('a09-silent-no-audit-on-high-risk-op','A09','Silent no-audit on high-risk op','MEDIUM',200,201,'Perform a sensitive op that leaves no trace.','Handoff issuance writes no audit entry; the absent log for a high-value action is the finding → flag.','handoff issuance path','d9fdc4a77f3de152e622cb6a321b0ed3a1a0003a8fbab6993ef4b157ebcef7fb','a12027bed4c420f5',TRUE,TRUE,46),
('a10-ssrf-server-fetches-your-url','A10','SSRF — server fetches your URL','EASY',100,220,'Make the server reach an internal-only endpoint.','DoctorLabService#importUrl fetches a caller URL with no allow-list; point it at http://localhost:8085/internal/ctf-metadata → flag.','ExternalCatalogueClient','06c51a0b98c018ac63fd4c9f577274e6099b10619e9e406cde3bc6993517c0c7','915adc9d70aeb515',FALSE,TRUE,47),
('a10-blind-ssrf-via-outbound-post','A10','Blind SSRF via outbound POST','HARD',300,228,'Exfiltrate via a server-initiated POST to your URL.','Prescription flow POSTs to a caller pharmacyCallbackUrl ; capture the outbound request (with internal data) → flag.','DoctorPrescribingService','f893db3bd2ed0f4860ad19d2554dc6c401fa4cbfddd2f809a35003160244ecd6','6e84a21e746b095b',FALSE,TRUE,48),
('a10-fail-open-promote-on-exception','A10','Fail-open promote on exception','MEDIUM',200,128,'Make a failed validation default to allow.','RefillQueueService promotes to READY on any validator exception (trigger via null quantity , #133/#134) → illegitimate dispense → flag.','RefillQueueService','7bbbd36a9ccc21a908d01ffa0aa01ca1ce5fca850a9a2973c9229045b65e8998','8e756b24677ee4b2',FALSE,TRUE,49),
('a10-toctou-race-double-dispense','A10','TOCTOU race — double dispense','HARD',300,131,'Win a check-then-act race.','dispense checks then acts without locking; fire concurrent requests to dispense twice → flag on the double.','RefillQueueService#dispense','a8d1f11833563856aff27fb16a8fc72aa282cd05ae4f2483589d9a762d3f2be7','14476197b7bb3368',TRUE,TRUE,50),
('a10-swallow-all-in-background-worker','A10','Swallow-all in background worker','MEDIUM',200,130,'Hide a failure in a scheduled job.','@Scheduled runWorker catches every exception silently; drive it into a bad state that goes unreported → flag in that state.','RefillQueueService (worker)','f971ff2079dfa74b60e7a0d645d8a2e1f85349c787e08d2ea3b7f76fa2580dd2','c41da5cac35a5a71',TRUE,TRUE,51),
('a10-verbose-exception-leakage','A10','Verbose exception leakage','EASY',100,136,'Read internal detail from an error message.','GET /api/refills returns raw exception text in failureReason ; trigger an error that leaks the flag/internal path.','RefillQueueService','492d002be73f5568751ce52ef3190c68e06f3f54561df09f32ea4674efae6f9f','b32d8a40e5ff10ef',FALSE,TRUE,52),
('a10-silent-null-on-failed-decode','A10','Silent null on failed decode','MEDIUM',200,254,'Turn a masked failure into a downstream flaw.','decodeBundle returns null on failure instead of erroring; the unchecked null drives a downstream NPE/bypass that exposes the flag.','DoctorReferralService#decodeBundle','3b6b5c1552f0d48faf8a4bd2fdf0d29dfaa8d9302a8b0c200cdf06d1cebc1959','2a56ec7af02cb042',TRUE,TRUE,53);
