# MediConnect CTF — Build Plan

**Goal:** Turn the two deliberately-vulnerable MediConnect apps into a professional, thesis-grade **Capture-The-Flag learning platform** for OWASP Top 10 (2025). Students read a lesson for a category, then exploit real vulnerabilities to retrieve hidden flag strings, tracking progress as they go.

**Audience:** Master's thesis. Must read as a deliberate, engineered teaching system — not a pile of bugs with checkboxes.

---

## 0. Primary framing: interactive companion to the thesis

The thesis already exists in substantial draft (`~/Downloads/master v2.md`, ~8,275 lines, Serbian): *"Bezbednost modernih web aplikacija: Analiza OWASP Top 10 (2025) kroz razvoj ranljive Spring Boot aplikacije."* It is structured per OWASP 2025 category (A01-A10), each with the same five parts:

- **X.1 Teorijska osnova** (theory)
- **X.2 CWE fokusi** (CWE focus)
- **X.3 Ranljivi primeri** (the vulnerable examples in *this* app)
- **X.4 Praktična demonstracija ... (Red Team PoC, Black Box)** (hands-on exploitation with open-source tools)
- **X.5 Mere mitigacije** (mitigation / secure implementation)

**The CTF platform is the interactive, playable version of §X.4.** The app it is built from is the same healthcare app the thesis documents, so the 53 challenges already ARE the thesis's vulnerable examples. This sets hard requirements:

1. **Black-box exploitable.** Every challenge must be solvable as an external attacker (HTTP only, no reading source), using the same open-source tooling the thesis cites. The demo narrative is "how an attacker with no source access does this." (Locally the student *can* read source, but the intended path and the thesis write-up are black-box.)
2. **Tool-aligned.** Each challenge names the real red-team tool(s) used in the thesis PoC.
3. **`fixed` branch = §X.5.** The remediation branch is the live version of every mitigation section. Per-category before/after.
4. **Professional, not toy.** Favor the impressive chains (SSRF, RCE, deserialization, JWT forgery, crypto cracking) as showpieces. More vulns may be added if a category's demo needs more punch.

### Red-team tool arsenal (from the thesis, black-box)

| Cat | Primary open-source tools |
|---|---|
| A01 Access Control | curl, Burp Suite, ffuf / wfuzz (forced browsing), manual JWT tampering |
| A02 Misconfiguration | nuclei (Spring Boot misconfig templates), curl, SSTI detection payloads |
| A03 Supply Chain | OWASP dependency-check, retire.js, curl |
| A04 Crypto | TShark / Wireshark, Bettercap (MITM), hashcat, John the Ripper, testssl.sh, openssl |
| A05 Injection | sqlmap, curl, Burp |
| A06 Insecure Design | Burp, curl (state-machine + session-timeout testing) |
| A07 Auth | hydra (brute force), ffuf, nuclei, curl (manual JWT alg:none / expiry) |
| A08 Integrity | curl (alg:none forge, HTTP-200 integrity oracle), Burp |
| A09 Logging | ffuf / wfuzz (parallel flooding), curl, log analysis |
| A10 SSRF / Exceptions | curl, Burp, ffuf (concurrency / error-flood), error-message analysis |

> Note: thesis titles put SSRF under both A01 (title) and A10 (dedicated section 11). We follow OWASP 2025: **SSRF lives in A10.** Reconcile the A01 title if needed when finalizing the thesis.

### 0.1 Two layers: the CTF game vs the thesis demonstration

The platform serves two audiences with different depth. Keep them distinct.

**Layer 1 - CTF (hypothetical students):** the 53 flags. One clear black-box path each, one obvious tool. Enough to learn the concept and score.

**Layer 2 - Demonstration (you / the thesis):** professional, escalating tradecraft on the *same* vulns, plus demonstration-only vulns (bucket 5). Students never need this; it exists so the write-up looks like real offensive security, not a tutorial.

**Exploitation ladder (per flag).** The app needs no change for this - you exploit the same endpoint multiple ways and document the progression. Example, the SQLi flag (#51):

| Rung | Method | Tool |
|---|---|---|
| 1 (student) | UNION dump via a browser/`curl` | curl |
| 2 | Manual boolean-blind extraction | curl + scripting |
| 3 | Time-based blind | curl / Burp Intruder |
| 4 (pro) | Automated with WAF-evasion tamper scripts | `sqlmap --tamper=...` |

Do the same per category: basic capture → intermediate → advanced/chained. This is where the "cool ways, different tools" live.

**Advanced arsenal (beyond the basic per-category tools in the table above):** Burp Collaborator (out-of-band SSRF/blind detection), sqlmap tamper scripts (WAF evasion), Turbo Intruder (race conditions, e.g. the #131 TOCTOU), chaining SSRF → internal endpoint → RCE (#229), ffuf recursion + custom wordlists, JWT forgery pipelines. Note the chains explicitly - a chained exploit (e.g. SSRF that reaches the Nashorn endpoint for RCE) is the most impressive thing you can show.

Deliverable per flag for the thesis: not just "the flag," but a short **exploitation ladder** (rungs + tools) and, where it exists, the **chain** it participates in.

---

## 1. Decisions (locked)

| Decision | Choice |
|---|---|
| Flag mechanic | **Real exploit-retrieved flags** — each challenge hides a secret string only reachable by actually performing the exploit. Student submits the string; server verifies against a stored hash. |
| Deployment | **Local, honor-based, modeled on OWASP Juice Shop.** No hosting, no AWS, no cost. Runs on the student's machine via docker-compose; scoring is a learning aid, not an anti-cheat boundary. Students are hypothetical, so the deliverable is the demoable artifact plus thesis, not a proctored cohort exam. |
| Progression | **Instructor-controlled unlock.** No in-app lessons. Which categories/challenges are open is driven entirely by the settings panel (default: all open, or A01-first — your call). |
| Lessons | **Out of scope for the app.** The thesis document covers how each vuln typically appears in real apps and how it's fixed. A separate **`fixed` branch** is the live remediation reference (the "how to fix" demo). |
| Instructor control | **Settings panel** — toggle which categories/challenges are open per session/student. This *is* the gating mechanism now. |
| Flag count | **Quality-driven, uneven per category** (~45-55 total). Keep every *distinct, interesting* exploit; rich categories (A01/A05/A08/A10) carry more, lean ones (A03/A09) fewer. No forced 3-per-category. |
| Duplicate policy | **Hybrid - "exploit ⇒ flag, or it's fixed." No silent dead-ends.** Identical patterns across N endpoints share one flag (any path captures it); a duplicate with a distinct twist is promoted to its own flag; a boring near-duplicate with no teaching value is fixed on the ctf-platform branch. |
| Scoring & hints | **E/M/H = 100/200/300.** Hints are **opt-in**: a student clicks to reveal one, which deducts a point. Progressive (multiple hints per challenge allowed). |
| Reset / replay | **Yes, per-challenge reset** so challenges can be replayed (useful for demoing). Plus a separate re-seed / DB-reset to recover polluted app state (risk in Risks section). |
| Cross-category lenses | #229 to A03 (RCE); SSRF taught via #220/#228 in A10. #226/#217 to A08. #148/#176 to A09. |
| Build direction | Build `ctf-platform` **from the `fixed` branch** (start remediated, re-open the 53) rather than fixing ~120 on top of the vulnerable base. Same end state, less error-prone. |
| XSS mechanism | **Server-side stored-payload detection.** The backend detects the injected payload landing in an admin-rendered context and awards the flag, with no headless browser. Proves the stored-XSS sink is reachable and unescaped without the Playwright infra. A real Playwright victim-bot stays optional as a Layer-2 demonstration-only showpiece (bucket 5) if time allows; it is NOT required to score the flag. |

---

## 2. Why this base works

Both apps are already purpose-built vulnerable teaching apps with inline `[A01]`–`[A10]` OWASP tags and a 261-row `VULN_FIX_MAP.md` catalogue. That catalogue is effectively a ready-made challenge inventory. Every OWASP 2025 category already has real, exploitable examples in the code — including genuine RCE (Nashorn `eval`, Java deserialization), SSRF, SQLi, IDOR, and `alg:none` JWT bypass. The frontend is a polished React + Tailwind + shadcn UI with `recharts` already installed for progress bars and score charts. **Adding the CTF layer is additive, not a rebuild.**

---

## 3. OWASP 2025 taxonomy note (thesis rigor)

The categories used below follow the OWASP Top 10 2025 list. **Before finalizing the thesis, verify each category name and ID against the official published 2025 release** — the ordering shifted from 2021 and some categories were merged (SSRF folded into "Mishandling of Exceptional Conditions"; "Vulnerable & Outdated Components" broadened to "Software Supply Chain Failures"). Each challenge below is assigned to exactly **one** category to keep flags distinct, even where a vuln plausibly fits several.

---

## 4. Branch strategy

Both repos branch from their current state before any CTF work.

**Backend** (`mediconnect-api`) — already a git repo, currently on branch `vulnerable` with uncommitted changes:
```bash
cd /Users/jelenarastovic/Downloads/mediconnect-api
git stash            # or commit the in-flight changes first — decide what to keep
git checkout -b ctf-platform vulnerable
git stash pop        # if stashed
```

**Frontend** (`mediconnect-frontend`) — **not under git yet.** Initialize first:
```bash
cd /Users/jelenarastovic/Downloads/mediconnect-frontend
git init
printf 'node_modules/\ndist/\n' >> .gitignore   # keep build artifacts out
git add -A && git commit -m "Baseline: vulnerable MediConnect frontend"
git checkout -b ctf-platform
```

**Three branches per repo:**
- **base (vulnerable)** — original untouched state. The thesis "before."
- **`ctf-platform`** — vulnerable app + CTF instrumentation (flags, scoring, progress, settings). This is what students run and hack.
- **`fixed`** - every core vuln behind the 53 flags remediated. The live "how to fix" reference for the thesis "after." Branch it from base (not from `ctf-platform`) so it's a clean remediation of the original, uncluttered by CTF plumbing.

```bash
# after creating ctf-platform:
git checkout -b fixed vulnerable   # backend; frontend: git checkout -b fixed <base>
```

Keeping the vulnerable base preserved gives the thesis its before/after: **base = vulnerable**, **fixed = remediated**, **ctf-platform = the interactive teaching artifact**.

---

## 5. Architecture

### 5.1 Honor-based scoring (the Juice Shop model)

Deployment is local and honor-based, exactly like OWASP Juice Shop: the student has the source, the DB, and the filesystem, so scoring **cannot** and **does not need to** be tamper-proof. Forging progress only cheats yourself. This is the established, citable model for deliberately-vulnerable training apps, and it removes the "hardened scoring layer" complexity entirely.

Best-effort measures we still keep (for cleanliness, not as a security boundary):
- **Flag hashes, not plaintext, in `ctf_challenge`.** Salted hash + metadata. Keeps a casual `SELECT` from spoiling every answer at once. Not claimed to resist a determined local user.
- **Flag plaintext lives at the exploit's endpoint** (record, file, internal endpoint, log line), so the *intended* way to get it is the exploit.
- **`/api/ctf/**` requires the logged-in user** — enough to keep progress coherent, not a wall.

Accepted and documented, not fought:
- **RCE / SQLi / arbitrary-SQL are end-game locally.** A student with #245/#229/#170/#168/#51 can read the DB and filesystem. That is fine under the honor model. We still **compartmentalize flags** (no single predictable location, so one RCE + `grep` can't trivially harvest *all* file-based flags in one shot — see risk §12.1), but we do not pretend it's airtight.

### 5.2 New backend module (`com.mediconnect.ctf`)

New Flyway migration `V26__ctf_platform.sql` + standard layered code. New tables:

- **`ctf_challenge`** — `id, slug, owasp_category (A01..A10), title, difficulty (EASY|MEDIUM|HARD), points, summary, objective, target_hint, flag_hash, flag_salt, intended_path (text), is_core (bool), sort_order`.
- **`ctf_progress`** — `id, user_id, challenge_id, status (LOCKED|OPEN|SOLVED), solved_at, attempts`. Keyed by `user_id` so a central board is a later join, not a migration. LOCKED/OPEN is derived from `ctf_settings`.
- **`ctf_settings`** — instructor overrides: `key, value` (e.g. `gating_enabled=true`, `open_categories=A01,A02`, per-challenge force-open). **This is the entire gating mechanism** — no lesson-completion dependency.
- **`ctf_submission`** — `id, user_id, challenge_id, submitted_flag_hash, correct (bool), created_at` — full attempt log (also demonstrates good logging, a nice A09 counterpoint).

New endpoints (`CtfController`, hardened):
- `GET  /api/ctf/challenges` — list with per-user status (respects settings-based gating).
- `GET  /api/ctf/challenges/{slug}` — detail: objective, hint, difficulty, points.
- `POST /api/ctf/challenges/{slug}/submit` — `{ flag }` → hash + compare → mark solved, award points, return correctness.
- `GET  /api/ctf/progress` — per-category %, total score, solved count, history for the personal leaderboard/timeline.
- `GET/PUT /api/ctf/settings` — instructor panel (which categories/challenges open, gating on/off).

### 5.3 Flag delivery per vuln type

The earlier worry — "how do you flag a vuln with no data to steal (XSS, SSRF, RCE)?" — resolved per type:

| Vuln type | How the flag is placed & captured |
|---|---|
| **Data exposure** (IDOR, SQLi, path traversal, broken access) | Flag string sits inside a record/column/file the student shouldn't reach. Reach it via the exploit, read the flag. |
| **SSRF** | An internal-only endpoint returns the flag, reachable only by pivoting through the SSRF-vulnerable fetch (`ExternalCatalogueClient#fetch`, `URL.openConnection` - resolves `http://`, `file://`, `jar:`, `ftp:` unfiltered and follows redirects). **Reachability caveat (must design for):** the naive `http://localhost:8085/internal/...` is NOT actually browser-unreachable in docker-compose, because the backend port is normally published to the host, so the browser can hit it directly and skip the exploit. Enforce server-only reachability one of these ways: (a) bind the internal endpoint to a second connector/port that is NOT published in `docker-compose.yml` (only reachable from inside the app container); (b) put a dedicated internal-metadata sidecar on the compose network with no host port mapping, reachable only container-to-container; or (c) gate `/internal/**` to accept only requests whose source is the app's own loopback. Prefer (a) or (b). The `file://` variant (#228) reads a local flag file through the same fetch and has no host-exposure issue. |
| **RCE** (Nashorn eval, Java deserialization, command injection) | Flag lives in a file on disk (`/ctf-flags/<slug>.txt`) or an env var readable only by executing code. Student's payload reads/prints it. |
| **Stored/Reflected XSS** | **Server-side detection (no browser):** the injected payload is stored via the vulnerable message/content endpoint, then a backend check (renders the field the same way the admin view would, or scans the stored value against the known payload signature) confirms the sink is unescaped and awards the flag. This proves the stored-XSS sink is reachable and un-encoded without running a headless browser. *(Optional Layer-2 demo only:* a real Playwright admin-bot that executes an `<img onerror>` / `<svg onload>` payload and calls back to `/api/ctf/xss-callback` - kept as a demonstration-only showpiece, not needed to score.) |
| **Crypto / Auth** (forge JWT with hardcoded secret or `alg:none`, mass-assign role=ADMIN, crack MD5) | Student forges/escalates to a privileged identity, then hits an admin-only endpoint that returns the flag. Capturing the flag *is* proving the auth bypass. |
| **Logging failures** | Flag hidden in a leaked log line (secrets logged verbatim), or awarded for successfully tampering/wiping the audit trail (`/logs/clear`) and the platform detecting the gap. |
| **Insecure design / exceptional conditions** | Flag exposed by triggering a fail-open business-logic path (e.g. refill eligibility bypass, `recentDays=0` full-table dump) or a verbose error leak. |

### 5.4 Frontend module

New pages under `src/pages/ctf/`, registered in `App.tsx`, sidebar entries added. Reuses existing shadcn primitives, toast system, React Query, and `recharts`. No lesson pages.

- **`/ctf/start` — Landing / onboarding:** the first thing a student sees. How the game works, the starter login, and ground rules — written in a plain human voice (full copy in Appendix A). Shown once on first run, always reachable from the sidebar.
- **`/ctf` — CTF Dashboard:** overall score, per-category progress rings (recharts), "continue where you left off," category cards showing locked/open/solved.
- **`/ctf/challenges/:category` — Challenge list:** the 3 core challenges for that category, difficulty badges, points, solved state.
- **`/ctf/challenge/:slug` — Challenge detail:** objective, target hint, progressive hints (optional, cost points), **flag submission form**, success animation.
- **`/ctf/progress` — Personal timeline/leaderboard:** solve history, score over time, category completion (recharts). Structured so a multi-user board slots in later.
- **`/ctf/settings` — Instructor panel:** toggle gating, open/close categories and individual challenges (writes `ctf_settings`).

Add a `VITE_API_URL` env var while here (currently `/api` is hardcoded) so the platform can point at different backends — small, worth it for a deployable thesis artifact.

---

## 6. The challenge set

Quality-driven and uneven per category (~45-55 flags), governed by the **hybrid duplicate policy** (§1): every exploitable path either awards a flag or is fixed — no successful-exploit-with-no-reward. The list below started as a rigid 3-per-category skeleton; it is being **expanded via a curation pass** over the 261-row `VULN_FIX_MAP.md`:

1. Cluster all 261 findings by vuln class (e.g. "IDOR", "SQLi", "stored XSS").
2. Per cluster, decide: one shared flag reachable by any instance / promote a distinct-twist instance to its own flag / fix a boring dead-end.
3. Produce the final `ctf_challenge` seed list + a per-instance disposition table (flag-here / same-flag / fix).

The skeleton below is the *minimum* per category; the curation pass adds the distinct extras. Each becomes a `ctf_challenge` row + a placed flag.

**A01 Broken Access Control**
- E — IDOR read: `GET /api/users/{id}` returns another user's profile (incl. `passwordHash`); flag in a target admin's record.
- M — Forced browsing: hit `/api/admin/**` as a PATIENT (permitAll); flag on an admin-only endpoint.
- H — Privilege escalation: use `AdminUserService#impersonate` to mint an admin token, reach admin-only flag.

**A02 Security Misconfiguration**
- E — Actuator leak: `/actuator/env` (`show-values: always`) exposes a secret → flag.
- M — Stacktrace leak: `include-stacktrace: always` reveals an internal path/value containing the flag.
- H — Runtime config override: `PUT /api/admin/ops/config/{key}` flips a setting guarding a flag.

**A03 Software Supply Chain Failures** (kept fully black-box - fingerprint over HTTP, then exploit; never read `pom.xml` to score)
- E - Identify the vulnerable component **black-box**: pull the version from `/actuator/env` or `/actuator/info` (A02 leaks it, `show-values: always`), or force a stacktrace (`include-stacktrace: always`) that prints `org.openjdk.nashorn...` frames; flag = the version string obtained this way (validates black-box dependency fingerprinting, not source review).
- M - Exploit the outdated component: reach the Nashorn `eval()` path (`DoctorPrescribingService#drugInteractions`) to run code → read flag. This is the A03 showpiece: outdated component turned into live RCE, entirely over HTTP.
- H - Planted malicious/backdoored dependency behavior: a seeded "helper" dependency triggers a backdoor on a magic HTTP input; flag returned when triggered (teaches transitive-dependency trust). Black-box: the student sends the magic input and gets the flag, no source needed. *(Requires planting a small fake dep, see §8 risks.)*

**A04 Cryptographic Failures**
- E — Exposed hash reuse: `passwordHash` returned in login response; crack the unsalted MD5 to log in as another user → flag.
- M — Forge a JWT with the hardcoded secret (`"mediconnect-super-secret-2024"`, `JwtUtil`), become admin → flag.
- H — Weak RNG: predict `AdminUserService#generateRandomPassword` (`java.util.Random`) to hijack a reset → flag.

**A05 Injection**
- E — SQLi UNION: `LabResultService#search` string-concat; UNION-dump a hidden flag column.
- M - Stored XSS: inject into message content; backend detects the unescaped payload in the admin-rendered context (server-side, no browser) and returns the flag.
- H — Command injection: `AdminOpsService#backup` (`dbName` → shell); read a flag file.

**A06 Insecure Design**
- E — Business-logic dump: `recentDays=0` returns the whole patient table incl. a flagged record.
- M — Fail-open eligibility: bypass `RefillQueueService` validator (null `quantity`) to dispense → flag.
- H — 30-day token + no binding: reuse a long-lived token in an unintended context to reach a flag.

**A07 Authentication Failures**
- E — Mass assignment: register with `"role":"ADMIN"` (`AuthService#register`) → admin-only flag.
- M — User enumeration + no rate limit: enumerate a valid account, brute-force, log in → flag.
- H — Expired-token replay: use the `SKIP_EXPIRY_PATHS` bypass (`/api/public|legacy|reports/`) with an expired token → flag.

**A08 Software or Data Integrity Failures**
- E — Unsigned record tamper: no `content_hash`; alter a medical record and have it accepted; flag confirms tamper.
- M — `alg:none` JWT: forge an unsigned token accepted by `JwtNoneVerifier` (note co-sign path) → flag.
- H - Java deserialization RCE: `DoctorReferralService#decodeBundle`/`inbox`/`accept` calls `ObjectInputStream.readObject()` with no class filter on the stored `bundlePayload`. `ReferralBundle` carries a **self-contained gadget** (`readObject()` runs `Runtime.exec("/bin/sh","-c",cmd)` when its `cmd` field is set), so no external gadget lib is needed. Black-box path: leak the FQCN `com.mediconnect.dto.ReferralBundle` (e.g. via A02 stacktrace), rebuild the class with matching `serialVersionUID=1L`, serialize with `cmd` set to read a flag file, base64, POST as a referral, trigger on inbox load.

**A09 Logging & Alerting Failures**
- E — Secrets in logs: `LoggingInterceptor` logs passwords/JWTs verbatim into `audit_logs`; read the flag from a leaked line.
- M — Audit tamper: `POST /api/admin/logs/clear` wipes the trail; flag awarded for erasing evidence of a seeded action.
- H — Unaudited privileged op: perform an impersonation/handoff that leaves no audit record; flag confirms the blind spot.

**A10 Mishandling of Exceptional Conditions (incl. SSRF)**
- E - SSRF to internal endpoint: `ExternalCatalogueClient` (via `customQueryUrl`) fetch an internal-only metadata endpoint → flag. **The endpoint must be unreachable from the host browser** (unpublished second connector or compose-internal sidecar, per the §5.3 reachability caveat), otherwise the SSRF is trivially skipped.
- M — SSRF `file://` scheme: read a local flag file through the same unrestricted fetch.
- H — Fail-open exception bypass: trigger a `catch(Throwable)` promote-to-READY path to reach a state that exposes a flag.

*(The skeleton above is the minimum floor. The curation pass below is the actual selection.)*

### 6.1 Curation result — 53 flags

The curation pass clustered all 261 findings by vuln class and selected **53 distinct flags** (finding IDs traceable to `VULN_FIX_MAP.md`). Full per-flag detail (objective, capture path, code ref, difficulty) lives in **`CTF_CHALLENGES_REVIEW.html`**.

| OWASP 2025 | Category | Flags | Flagged finding IDs |
|---|---|---|---|
| A01 | Broken Access Control | 8 | 42, 94, 93, 191, 38, 56, 151, 173 |
| A02 | Security Misconfiguration | 4 | 28, 32, 64, 169 |
| A03 | Software Supply Chain / dangerous libs | 4 | 206, 204, 205, 229 |
| A04 | Cryptographic Failures | 5 | 20, 11, 16, 7, 153 |
| A05 | Injection | 8 | 51, 194, 55, 170, 48, 47, 46, 264 |
| A06 | Insecure Design | 3 | 59, 43, 187 |
| A07 | Authentication Failures | 4 | 25, 27, 33, 34 |
| A08 | Data Integrity Failures | 7 | 245, 210, 19, 225, 50, 177, 188 |
| A09 | Logging & Alerting Failures | 3 | 65, 160, 201 |
| A10 | Exceptional Conditions + SSRF | 7 | 220, 228, 128, 131, 130, 136, 254 |
| | **Total** | **53** | |

**Per-instance disposition (hybrid policy):**
- **Shared-flag clusters** (one flag reachable via any path): IDOR-by-id (42/52/36/89/107/197/57), returns-all BOLA (93/40/62), identity-spoofing (56/109/186/231/257), path-traversal-write (47/218/181), weak-signing (225/216/200), silent-no-audit (201/222/232/253/209).
- **Fix on ctf-platform branch** (boring dead-ends, no teaching value): 92, 95, 90, 91, 113, 139, 145, 23, 63, 147, 180, 60, 262, 263, 129, 132, 135, 137, 159, 211, 223, 133, 134, 138, and the other BORING-DUP rows.
- **Cross-category collisions to decide once:** 229 (Nashorn RCE = A03 vs SSRF = A10, same endpoint), 226/217 (plaintext signing key: A04 vs A08), 148/176 (hard-delete: A01 vs A09). Pick one teaching lens each; don't double-count.

---

## 7. Learning content — out of scope for the app

No in-app lessons. The theory (how each vuln typically appears in real apps) and the remediation (how it's fixed) live in the **thesis document** and are demonstrated live by the **`fixed` branch**. The app itself stays a pure hacking surface: challenges + flags + progress.

The `fixed` branch is the deliverable that carries the "how to fix" story: each of the core vulns behind the 53 flags remediated with the standard correct pattern (parameterized queries, real access-control checks, salted+strong password hashing, JWT signature verification + short expiry, SSRF host allow-list, safe deserialization, output encoding, etc.). The thesis references specific `fixed`-branch diffs per category.

---

## 8. Fate of every finding (explicit)

Every one of the 261 findings lands in exactly one bucket on the **ctf-platform branch**. No finding is left in an ambiguous "maybe exploitable, no reward" state — that is the dead-end we're avoiding.

| Bucket | What happens | Count |
|---|---|---|
| **1. Flagged** | Kept and instrumented with a placed flag — the 53 graded challenges (§6.1). | 53 |
| **2. Shared-flag instances** | Left vulnerable but point at the *same* flag as their cluster lead, so any path scores. | ~40 |
| **3. Fixed (dead-end dups)** | Boring near-duplicates of a flagged class, remediated so a successful exploit can't go unrewarded. | ~60 |
| **4. Stability fixes** | Break the app itself, not pedagogy — remediated regardless: `max-file-size: -1` (OOM), `System.exit(0)` restart, Flyway `ddl-auto` conflict. | few |
| **5. Demonstration-only vulns** | Cool-to-exploit vulns NOT in the 53, kept exploitable purely so the **thesis** can demonstrate them with professional tradecraft. Not listed as challenges, no `ctf_challenge` row. | curated set |
| **6. Fixed** | Everything else: genuinely boring dead-ends + stability breakers (`max-file-size: -1`, `System.exit`, `ddl-auto` conflict). | remainder |

**Revised decision (was "fix all non-flagged"):** keep the **cool-to-demonstrate** vulns even though they aren't CTF challenges; fix only the boring dead-ends and stability breakers. Model:

> **ctf-platform = the `fixed` branch + 53 CTF vulns + a curated demonstration-only set.**

No accidental dead-ends: demonstration-only vulns aren't advertised in the challenge list, and it's honor-based local anyway. The demonstration set is where the network-technique PoCs live (Bettercap MITM, TShark sniffing, ffuf/error floods) plus any extra showpieces. Build direction unchanged: build *from* `fixed`, re-open the 53 CTF vulns + the demonstration set.

---

## 9. Phasing & effort

| Phase | Work | Rough effort |
|---|---|---|
| 0 | Branch all three per repo (base / ctf-platform / fixed); git-init frontend; freeze vulnerable baseline | 0.5 day |
| 0.5 | **Curation pass:** cluster 261 findings by vuln class → final ~45-55 flag list + per-instance disposition (flag / same-flag / fix) per hybrid policy | 1 day |
| 1 | Backend CTF module: `V26` schema, entities, repos, `CtfController`, flag hashing/verification, hardened `/api/ctf/**` auth, `ctf_settings` | 2–3 days |
| 1b | **Tag all 53 as retrieval vs behavioral-detection** (Blocker §12.1) and design the success hook for each of the ~15 behavioral ones (emit flag only when the illegal action succeeds) | 1 day |
| 2 | Place the 53 flags: internal SSRF endpoint (unpublished connector/sidecar), flag files for RCE, server-side XSS detection, seeded flag records/columns; instrument the ~15 behavioral challenges; seed `ctf_challenge` rows | 5-6 days |
| 3 | Frontend CTF pages: dashboard, challenge list/detail, flag submission, progress (recharts), settings panel; `VITE_API_URL` | 2–3 days |
| 4 | `fixed` branch: remediate the core vulns behind the 53 (the "after" reference) | 3–4 days |
| 5 | Stability fixes; DB re-seed control (§12.4); end-to-end playthrough of all 53; balance points/difficulty | 2-3 days |
| 6 | Thesis writeup: architecture, taxonomy mapping, before/after, evaluation | ongoing |

Phases 1–3 can partly overlap (contract-first: agree the `/api/ctf` shape, then build both ends). Phase 4 (`fixed`) is independent and can run in parallel with 2–3. Ballpark **3.5–4 focused weeks** to a demoable platform, plus thesis writing. (Revised up from an earlier 2.5-3wk figure once the ~15 behavioral-detection hooks, the full 53 placements, and the DB-reset control are counted, none of which were in the original estimate.)

---

## 10. Thesis contribution angle

Framing that makes this defensible as a master's thesis rather than an app:
- **Prior-art anchor:** explicitly modeled on OWASP Juice Shop (local, honor-based, source-available training app). Citing the established model resolves the "scoring isn't tamper-proof" objection up front (§5.1).
- **Differentiation from Juice Shop (an examiner WILL ask — nail this):**
  - **OWASP Top 10 2025** mapping specifically (Juice Shop predates it and maps to older lists).
  - **Realistic healthcare/EHR domain** with coherent seed data (patients, labs, prescriptions, referrals), not a generic shop — domain-authentic vulnerabilities (clinical-record tamper, prescription workflow bypass, PII exposure).
  - **Paired `fixed` branch** as a first-class remediation reference: every vuln has a demonstrable before/after diff. Juice Shop has no built-in "here's the fix" companion.
  - **Curation method** itself: a documented process reducing 261 raw findings to 53 pedagogically-distinct challenges under a stated duplicate policy.
- **Mapped taxonomy:** every challenge tied to exactly one OWASP 2025 category.
- **Authentic exploitation:** capturing a flag requires performing the real attack, not ticking a box.
- **Evaluation (given hypothetical students):** frame as a walkthrough/demonstration and a qualitative self-assessment against learning objectives per category, plus the before/after security-posture comparison from the `fixed` branch. Do not over-claim a controlled cohort study you won't run.

---

## 11. Open questions (need your input before/while building)

Resolved: lessons out of scope (thesis + `fixed` branch cover remediation); A03 planted-dependency challenge kept **and confirmed black-box** (fingerprint via actuator/stacktrace, exploit Nashorn RCE over HTTP, §6 A03); XSS goes **server-side stored-payload detection** (Playwright bot demoted to optional Layer-2 showpiece).

Resolved: hints (opt-in click, -1 pt), reset/replay (yes + re-seed), points (100/200/300), cross-category lenses, build-from-fixed, **XSS = server-side detection (no browser required)**, grading model (local honor-based / Juice Shop), non-flagged fate (fix boring dead-ends, keep cool demonstration-only vulns), **flag count = 53** (§6.1; supersedes every stale "30" figure), **behavioral-vs-retrieval tagging budgeted** (Phase 1b), **DB re-seed control** added (§12.4).

Still open:
1. **Plan file home:** this plan currently lives in the backend repo. Move it somewhere neutral, or keep a copy in each repo?
2. **Per-challenge black-box verification:** before locking each of the 53, confirm the thesis's §X.4 PoC actually reproduces against the app with the named tool. Some thesis demos (A04 MITM/sniffing, A09 log-flood, A10 error-flood) are network/technique demos that may not map to a captured flag - decide which are graded challenges vs thesis-only demonstrations.

---

## 11b. Seed data & accounts strategy

**Augment, don't replace.** The existing seed (admin, 3 patients, 3 doctors, labtech, pharmacist, cross-linked appointments/labs/prescriptions/messages, per `TEST_ACCOUNTS.md`) is realistic and stays. `patient1` is the player; the rest are escalation targets.

Additions (all in a dedicated, idempotent `V26+` CTF seed migration, separate from base app seed):

1. **Volume for realism (the accuracy win).** Bulk-generate ~40-50 synthetic patients (Faker-style: realistic but fake names/MRNs/diagnoses/labs/prescriptions). A 3-row dump looks like a lab exercise; a 50-row dump looks like a real breach - it makes the sqlmap `--dump`, IDOR-enumeration, and PII-exposure demos land as professional in the thesis.
2. **Challenge-specific crafted accounts/records:**
   - a target with a **crackable-but-not-trivial** password (dictionary + rules) so hashcat/John (#20, #153) is a real crack, not instant;
   - a **locked** account for brute-force + lockout-bypass (#27, #149);
   - a **secret-bearing** admin/internal record so access-control flags (#42, #38, #151) land on confidential-looking data;
   - an **internal-only endpoint** (`/internal/ctf-metadata`) for SSRF flags (#220) to reach.
3. **Flag placement as plausible secrets.** Keep the `flag{...}` format for verification, but embed each in a field where a real secret would live (an internal reference, a restricted note), not an obvious name column - so capture feels like genuine data exfiltration.

Idempotent seed = the reset/re-seed feature (§12.4) restores a known clean state.

---

## 12. Risks & unresolved design problems

Surfaced in a "think harder" pass. Blockers change the architecture — resolve before Phase 1.

**Resolved by the honor-based / Juice Shop decision:** old #6 (flag secrecy) is answered by prior art, not fought. Old #8 (multi-user leaderboard) and #12 (flag sharing) are moot (no cohort). Old #1 (RCE/SQLi vs flag store) is downgraded from blocker to "accepted, with light compartmentalization" (§5.1).

### Blockers (still change the build)
1. **~15 challenges are behavioral, not retrieval, so they need server-side success detection.** #43, #59, #131, #187, #188, #201, #254, #130, #169 have no string to find; the endpoint must be instrumented to emit a flag only when the illegal action succeeds. This is exactly how Juice Shop detects challenge completion, so the pattern is proven, but it IS backend work per challenge. **Now budgeted: Phase 1b** tags every one of the 53 as **retrieval** vs **behavioral-detection** and designs the hook for each behavioral one; Phase 2 effort raised to 5-6 days to build them.
2. **RESOLVED - XSS goes server-side (no browser).** React `dangerouslySetInnerHTML` sets innerHTML, so `<script>` won't execute and payloads would need `<img onerror>` / `<svg onload>`, and a true victim would need a real Playwright headless browser. **Decision: skip the browser.** Flag is awarded by server-side detection that the stored payload lands unescaped in the admin-rendered context (§5.3, §1). The authentic Playwright admin-bot is retained only as an optional Layer-2 demonstration-only showpiece (bucket 5), not required to score.
3. **RESOLVED - deserialization RCE (#245) works as-is, no gadget lib needed.** Verified against the code: `ReferralBundle` (`src/main/java/com/mediconnect/dto/ReferralBundle.java`) has a self-contained gadget - its `readObject()` runs `Runtime.getRuntime().exec("/bin/sh","-c",cmd)` when the `cmd` field is populated, and `DoctorReferralService.inbox()`/`accept()` call `ObjectInputStream.readObject()` on the stored blob with no `ObjectInputFilter`. `pom.xml` has no classic gadget chain (openpdf/nashorn/jjwt/mapstruct/freemarker only) but none is required. Black-box exploit: leak the FQCN via an A02 stacktrace, rebuild the class (`serialVersionUID=1L`, String fields), serialize with `cmd` set, base64, POST as a referral, trigger on inbox load. Keep #245 as a graded flag.
4. **State pollution - add a DB re-seed control (recommended).** Destructive challenges (#65 wipe logs, #177 override, #188 overwrite, cascade deletes) wreck seed data and can block later challenges. **Recommendation: two distinct resets** - (a) *per-challenge progress reset* (already planned, for replay/demo) and (b) *full DB re-seed* that reruns the idempotent V26 CTF seed to restore clean app state. Expose (b) two ways: a `docker-compose down -v && up` one-liner for the demo, and an admin-panel button hitting a re-seed endpoint. Cheap because the seed is already idempotent (§11b). Additionally isolate each destructive challenge onto dedicated throwaway records where practical so one solve doesn't brick the next. (Juice Shop sidesteps this by keeping challenges independent + offering a restart; the re-seed is our equivalent.)

### Housekeeping
5. **Light flag compartmentalization** (§5.1): no single predictable flag location, so one RCE + `grep` can't harvest every file-based flag at once. Best-effort, not a wall.
6. **Setup + tooling** for the demo: a one-command docker-compose bundling app + DB + frontend, and a short "tools you need" note (curl/Burp/sqlmap/a JWT tool). Just for you demoing, so keep it minimal.
7. **Hints are unwritten content**, 53 sets. Write / generate-then-edit / drop. (Juice Shop offers tiered hints; nice-to-have, not required.)
8. **A03 planted backdoor dep**: reproducible Maven mechanics (local `.m2` / local module).
9. **Attack surface is the API, not the UI**: most exploits are curl/Burp against `/api`; the React CTF pages are the scoreboard; only XSS needs the UI. Note per-challenge which surface.
10. **Cross-category lenses unpicked**: #229 (RCE vs SSRF), #226/#217 (A04 vs A08), #148/#176 (A01 vs A09), one lens each.
11. **SSRF internal endpoint must not be host-reachable** (§5.3): a plain `localhost:8085/internal/...` is reachable from the host browser once the backend port is published in compose, which lets a student skip the exploit. Serve `/internal/**` on an unpublished second connector or a compose-internal sidecar with no host port mapping. Confirmed exploitable path: `ExternalCatalogueClient` uses `URL.openConnection()`, so `http://` and `file://` both resolve and redirects are followed.

---

## Appendix A — Landing page copy

Plain-voice onboarding text for `/ctf/start`. Edit freely — it should sound like you, not a manual.

> ### Welcome to MediConnect CTF
>
> MediConnect looks like a normal hospital patient portal — appointments, lab results, prescriptions, messages between patients and doctors. It's also riddled with security holes, on purpose. Your job is to find them and break in.
>
> **Start here:**
> 1. Log in as the patient below and just use the app for a few minutes — book an appointment, open your lab results, message a doctor. Get a feel for how it's meant to work before you start bending it.
> 2. Open the **Challenges** page. Each one tells you what you're trying to pull off and roughly where to poke. Pick a category, read the goal, and go digging.
> 3. When an exploit lands, you'll turn up a flag — a string that looks like `flag{...}`. Drop it into the challenge to score it.
> 4. Watch your progress fill in as you go. The nastier the bug, the more it's worth.
>
> **Your account:**
>
> | Role | Username | Password |
> |---|---|---|
> | Patient (this is you) | `patient1` | `12345` |
>
> That's the only login you're *given*. There are doctors, a pharmacist, and an admin in here too — but getting into their accounts is half the challenge. If you find yourself logged in as someone you shouldn't be, you're doing it right.
>
> **A few ground rules:**
> - This whole thing is a sandbox built to be attacked. Nothing you do here touches a real system or real patients.
> - The easy ones are *meant* to feel too easy — that's how the concept sinks in before the hard stuff.
> - Stuck on one? There are hints, but each costs you a few points, so try on your own first.
>
> Have fun. Break things.

**Full account roster** (instructor reference — lives in `TEST_ACCOUNTS.md`, gitignored). Students get only `patient1`; the rest are targets. Reveal more per session via the settings panel if you want to scope a challenge to a specific role.

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Patient (starter) | `patient1` | `12345` |
| Patient | `patient2` | `patient123` |
| Patient | `patient3` | `patient123` |
| Doctor (GP) | `doctor1` | `password` |
| Doctor (Cardiology) | `doctor2` | `doctor123` |
| Doctor (Pediatrics) | `doctor3` | `doctor123` |
| Lab Tech | `labtech1` | `labtech123` |
| Pharmacist | `pharmacist1` | `pharma123` |

The seed data is rich (real-looking patients, appointments, labs, prescriptions, message threads) — good for making flag placement feel natural rather than obviously bolted on.
