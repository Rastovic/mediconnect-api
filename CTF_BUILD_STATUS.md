# CTF Build Status — RESUME HERE

Handoff so a fresh session can continue placing flags. **Read this first, then `CTF_FLAG_PLACEMENT.md` for the live status table.**

## Where we are
- Building the MediConnect CTF platform per `CTF_PLATFORM_PLAN.md`. Phases 0/1/2/3 done and verified; **Phase 5 (validation + polish) done**. Remaining: Phase 4 (`fixed` branch) + Phase 6 (thesis).
- **Progress: 53 / 53 flags PLACED and verified live.** Highest Flyway migration: **V47** (next = V48).
- Final batch (V41-V47): #11, #136, #25, #27, #33, #34, #19, #225, #50, #46, #47, #264, #194, #20, #153, #28, #32, #228, #130 - all captured live and regression-checked.

### Phase 5 results (validation + polish)
- **Fresh-reseed reproducibility bug FIXED.** A `docker compose down -v` full rebuild failed at V30: it hardcoded `sender_id=11/receiver_id=10` (the polluted dev DB's ids), which do not exist on a clean deterministic seed, so the messages FK blew up and the app would not start. V30 now resolves participants by username (`doctor2`, `patient3`), so it seeds correctly on any clean rebuild. Canonical clean ids: patient1=user2/patient1, patient2=user4/patient2, patient3=user5/patient3, doctor1=user3/doctor1, doctor2=user6/doctor2.
- **Full clean playthrough PASSED.** `down -v` rebuild applies all 47 migrations, seeds 53 challenges. All 53 flags submit `correct` via `POST /api/ctf/challenges/{slug}/submit`; a wrong flag is rejected; `GET /api/ctf/progress` totals **10500/10500**, 53/53 solved, every category full.
- **Scoring + hints verified:** E/M/H = 100/200/300; hint deduction 0/1/2/3/4 hints -> 100/75/50/25/25 (25% floor); a second submit of an already-solved challenge awards 0.
- **Id-sensitive exploit paths re-verified on the clean seed:** #94 uses `?userId=6`, #7 uses `by-user/4`, #42 `patient/2`, plus #51/#93/#191. Stale capture ids in `placed.json` (#94 was userId=10, #7 was by-user/9) updated to the clean ids + docs regenerated.
- **FE contract confirmed aligned:** `mediconnect-frontend` builds clean; `src/api/ctf.ts` fields (`pointsAwarded`, `possibleScore`, `totalCount`, `owaspCategory`, `scoreEarned/Possible`) match the BE DTOs.
- **DB re-seed control (§12.4) both paths now exist:** (a) `docker compose down -v && up` = full clean restore of app data + flags (authoritative, verified); (b) new `POST /api/ctf/progress/reset` = game reset (wipes caller progress + submissions + all in-memory behavioral marks via `CtfBehaviorRegistry.clearAll()`) for scoreboard replay without nuking the DB.
- Work is on branch **`ctf-platform`** in BOTH repos (this API + `~/Downloads/mediconnect-frontend`). All changes uncommitted (user reviews before commit).
- Constraints (standing): only touch `mediconnect-api` + `mediconnect-frontend`; no brain workflows; keep FE/BE compatible; **bug-check after every big change** (package + live verify + regression).

## Run / verify environment
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)          # app needs JDK 21
cd ~/Downloads/mediconnect-api
docker compose up -d mysql                                 # container mediconnect-mysql, root/root, db mediconnect_db
./mvnw -q -o -DskipTests package
# #245 deserialization flag lives ONLY in this env var:
CTF_A08_FLAG='flag{a08_java_deserialization_rce_990dc2}' nohup java -jar target/mediconnect-api-0.0.1-SNAPSHOT.jar > /tmp/mc-app.log 2>&1 &
# wait for "Started MediconnectApiApplication"; ports 8085 (main) + 8099 (internal SSRF, loopback)
```
Login (note: field is `username`, not email): `{"username":"patient1","password":"12345"}` → JWT. patient1 = user id 2 / patient id 1.

## Per-challenge rhythm (repeat for each flag)
1. Find the vulnerable sink (grep controller/service).
2. Place the flag by its type (see below).
3. `./mvnw -q -o -DskipTests package`, kill old jar + free ports 8085/8099, restart.
4. **Verify the exploit live** (curl) + regression on 1-2 prior flags.
5. `python3 ctf-tooling/add_*.py`-style: add the entry to `ctf-tooling/placed.json` as `"<finding>": ["<where placed>", "<how-to-capture HTML>"]`. (Write the update as a .py FILE, not an inline heredoc — `$` in heredocs breaks on `${...}`.)
6. Regenerate docs: `python3 ctf-tooling/regen_docs.py ctf-tooling /Users/jelenarastovic/Downloads/mediconnect-api` (idempotent; rewrites `CTF_CHALLENGES_REVIEW.html` badges + `CTF_FLAG_PLACEMENT.md`).
7. Cleanup: `DELETE FROM ctf_submission; DELETE FROM ctf_progress;` + remove any test rows; stop app.

## Placement mechanisms (all built, reuse them)
- **Retrieval / data exposure**: seed the flag into a record/column the exploit returns (Flyway `Vxx` migration).
- **UNION SQLi**: `ctf_secret` table (app collation utf8mb4_unicode_ci), dumped via UNION.
- **RCE / path-traversal / SSTI / XXE (file read)**: put a flag file in `src/main/resources/ctf-flags/<slug>.txt` — `CtfFlagFileInitializer` copies every `ctf-flags/*` to `<user.dir>/ctf-flags/` on startup (and `.ftl` also into `notes/templates/`). Exploit reads it; reflected flags need NO ctf_secret row (submit checks the V26 hash).
- **SSRF (#220)**: internal endpoint `/internal/metadata` on unpublished connector **8099** (`CtfInternalConnectorConfig`); flag in `ctf_secret`.
- **Behavioral** (illegal action succeeds, no string to read): call `com.mediconnect.ctf.CtfBehaviorRegistry.mark("<slug>")` on the vulnerable success path, add a `ctf_secret` row, award via `GET /api/ctf/behavior/{slug}`. Registry has `bump(key)` for race detection (#131).
- **Admin-gated** (forge/escalate to ADMIN): `GET /api/ctf/admin-secret` returns a `ctf_secret` flag only to an ADMIN principal.
- **Stored XSS (#55)**: `GET /api/ctf/xss/check` detects an executable payload in the caller's stored messages.

## Flag data
- `ctf-tooling/chals.json`: all 53 challenges (finding id → slug, flag plaintext, difficulty, behavioral, path). This is the source of truth for flag strings.
- `ctf-tooling/placed.json`: the 53 placed entries (drives the docs).
- Hashes for all 53 seeded in `V26__ctf_platform.sql` (salted sha256). Any correct flag string is submittable now; "placed" means the exploit path yields it.

## Remaining: NONE — all 53 placed

Final batch placement notes (V41-V47):
- **A07 auth** (V42): #25 user-enum + #27 no-rate-limit marks on the wrong-password branch of `AuthService.login` (#25 fires on the failed-probe path, NOT on every successful login, so legit logins do not self-mark it); #33 expiry-skip fixed a real gap (`extractUsername` threw on expiry BEFORE the skip-path branch, now recovers subject from `ExpiredJwtException.getClaims()`) + #34 fail-open mark, both in `JwtAuthenticationFilter`. A recovered-subject-but-deleted-user is caught as `UsernameNotFoundException` and does NOT mark #34 (avoids the #33/#34 double-mark).
- **A04** (V41/V46): #11 seeded `records_svc` user with password_hash = flag; #20 seeded `crackme` (MD5 of "sunshine"), login-mark; #153 `PredictablePasswordGen.forUser(id)` seeds `Random(SEED_BASE+id)`, reset uses it, login-with-predicted-pw marks.
- **A08** (V43): #19 asymmetric-alg header detection + #225 new `POST /prescriptions/{id}/verify-signature` (recomputed MD5 accepted) + #50 replace-attachment mark.
- **A05** (V43/V44/V45): #46/#47 marks in `MedicalRecordService.uploadAttachment`; #264 formula-cell mark in `DoctorAppointmentService.exportCsv`; #194 flag in `ctf_secret`, boolean-extracted via `GET /api/doctor/patients?q=`.
- **A10** (V41/V47): #136 flag in a seeded refill `failure_reason`; #228 outbound POST attaches header `X-Internal-Signing-Token`=flag to `pharmacyCallbackUrl` + mark; #130 (was DEFERRED) now triggerable: a refill with `requestedBy=-999` poisons the `@Scheduled runWorker` tick, swallowed by the generic catch + mark. The poison row is moved to `FAILED` before the throw so it drops out of the REQUESTED queue - one poison costs the batch a single tick, it does NOT permanently stall refill processing for other challenges on a shared instance.
- **A02** (V46): #28 new `CorsProbeFilter` marks on cross-origin state-changing requests; #32 NoOp plaintext-compare path in `AuthService.login` (submit the leaked hash as the password).

## Key files
- Module: `src/main/java/com/mediconnect/ctf/` (entities, repos, `CtfService`, `CtfController`, `CtfBehaviorController`, `CtfBehaviorRegistry`, `CtfFlagFileInitializer`, `CtfInternalConnectorConfig`, `InternalMetadataController`, `CtfXssController`, `PredictablePasswordGen`, `CorsProbeFilter`).
- Migrations `V26`–`V47`. Frontend CTF pages under `mediconnect-frontend/src/pages/ctf/` (builds clean; don't break the `/api/ctf` contract in `src/api/ctf.ts`).
