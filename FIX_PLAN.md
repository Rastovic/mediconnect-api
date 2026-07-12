# MediConnect Fix Plan

> Companion to `VULNERABLE_CONFIG.md` (vulnerability list) and `VULN_FIX_MAP.md` (per-vuln tracker).
> This file is the **how**: best-practice patterns, ordering, risk callouts, and ready-to-paste Claude Code prompts for each fix phase.

---

## 0. Ground rules before starting

### 0.1 Branch + baseline

```bash
git checkout -b fixes origin/vulnerable
git tag pre-fix-baseline                  # rollback anchor
./mvnw test                               # capture baseline (currently 1 test pass)
# Frontend
cd ../mediconnect-frontend && npm run build && npx tsc -b --noEmit
```

### 0.2 Smoke endpoints (must stay green between phases)

Hit these after every phase. Any unexpected 5xx = stop and fix before continuing.

```
POST /api/auth/register     (new patient)
POST /api/auth/login        (admin / admin123)
GET  /api/users             (admin token)
GET  /api/appointments      (patient token)
POST /api/messages          (patient → doctor)
GET  /api/medical-records   (doctor token)
GET  /api/lab-results/search?patientId=1
GET  /api/prescriptions     (doctor token)
GET  /api/admin/health      (admin token)
GET  /api/doctor/patients   (doctor token)
```

Frontend: log in as admin → users page renders; as patient → dashboard renders; as doctor → roster renders.

### 0.3 Golden rules for every prompt below

1. **Never delete a route without a replacement** — UI is wired to specific paths. If the route is too dangerous to keep, return `405` or `404` with a generic body, don't `@Deprecated`-comment-and-leave-running.
2. **Backend DTO removals are coordinated with frontend** — if you remove `passwordHash` from `UserDto`, search frontend for `passwordHash` and replace with a placeholder or hide the column in the same commit.
3. **Database changes are additive first, destructive later** — add a column / new table → backfill → switch code → drop old column in a later phase.
4. **Roll forward, don't roll back** — if you break a smoke test, fix it in the next commit, don't revert. The fix branch should always go forward.
5. **One OWASP category per commit** if possible. Makes review easier and rollback surgical.
6. **Run `./mvnw test` + frontend `tsc -b` after every phase.**

### 0.4 Dependencies between phases

```
Phase 1 (config)          → no blockers, do first
Phase 2 (crypto + secrets) → blocks 3, 12
Phase 3 (auth/JWT)         → blocks 4
Phase 4 (authz / A01)      → blocks 8, 11, 12, 13
Phase 5 (injection)        → independent
Phase 6 (file upload)      → independent
Phase 7 (XSS sanitisation) → blocks frontend pieces in 12
Phase 8 (SSRF/SSTI/XXE/deser) → independent but heavy
Phase 9 (integrity / signing) → blocks 10
Phase 10 (state machines + A10) → independent
Phase 11 (logging hygiene)     → after 1
Phase 12 (frontend hardening)  → after 2, 3, 4, 7
Phase 13 (dangerous admin)     → after 4
Phase 14 (final sweep)         → last
Phase 15 (appointments tab)    → after 1, 3, 4, 5, 7, 11
```

> **Note on removed AI Assist surface:** vulnerabilities #247, #248, #249, #250, #251, #255 were marked 🚫 wont-fix when the AI Assist tab was deleted in the Appointments-tab redesign (Phase 15, see `APPOINTMENTS_PLAN.md`). Skip those rows; the underlying files (`DoctorAIController.java`, `DoctorAIService.java`, `DoctorAIAssistantPage.tsx`, `AIAssistantPanel.tsx`) no longer exist on the vulnerable branch.
>
> **Note on removed Telemedicine surface:** vulnerabilities #236–#244 (Module E) were marked 🚫 wont-fix when the Telemedicine tab was deleted from the Doctor Console. Skip those rows; backend (`DoctorSessionController`, `DoctorSessionService`, `TelemedicineSession` entity + repo + DTO) and frontend (`DoctorSessionsPage`, `DoctorSessionRoomPage`, `TelemedicineRoom`) all gone. The `V23__telemedicine_sessions.sql` migration is left in place so Flyway history stays linear; the resulting `telemedicine_sessions` table is orphaned. Phase 8's SSRF coverage (#240) drops one demo target; Phase 11's logging coverage (#242) drops one too.
>
> **Note on Appointments UX scoping:** the Appointments page now resolves the active doctor from `/api/doctors/profile` (logged-in session) instead of prompting for a Doctor ID. This is a UX-only change — the [A07] (#257) and [A01] (#268) weaknesses on the backend are unchanged: `actorDoctorId` is still accepted from the request body when the UI's opt-in "Override actor" checkbox is checked, and the conflicts endpoint still returns every doctor's overlaps when called without a `doctorId` filter. Phase 15.1 + 15.6 fix prompts unchanged.
>
> **Note on patient view UI scrub — 2026-06-19 (#43, #92):** the "Update status" dropdown + button inside the `AppointmentsPage.tsx` detail modal is now wrapped with `{!isPatient && (...)}` so PATIENT users cannot change appointment status through the UI. The backend vulnerabilities are intact. Phase 10 prompts for #43 (state machine) and #92 (ownership IDOR on PUT) are unchanged — fixes are server-side only and do not depend on the presence of the UI control.
>
> **Note on patient profile UI scrub — 2026-06-19:** the "Account Activity" card was removed from the patient-facing `ProfilePage.tsx`. It displayed `failedLoginAttempts`, `lockedUntil`, and `createdAt` sourced from `GET /api/users/{id}`. This card had no Phase 12 fix entry (the data leak is backend-level, covered by the `UserDto` mass-exposure and IDOR families in Phase 4). No Phase 12 prompts are affected.

---

## Phase 1 — Configuration hardening (low risk, no UX change)

**Covers:** vulns #1–#6, #31, #71–#75
**Risk:** very low — config + filter limits
**Estimate:** 1–2 hours

### Best practices

- Secrets via env vars, never committed (`${DB_PASSWORD:fallback}` syntax only with safe fallback).
- Disable `show-sql`/`format_sql` in `application.yaml`; if needed for local dev, use `application-local.yaml` profile.
- `ddl-auto: validate` so Hibernate doesn't silently mutate schema — Flyway owns migrations.
- Actuator: expose only `health` + `info`; protect even those behind admin role.
- `server.error.include-*: never`. Use a `@RestControllerAdvice` that returns generic JSON.
- Bound multipart and request body sizes.
- Add the standard security headers via `HttpSecurity#headers` (HSTS, X-Content-Type-Options, X-Frame-Options DENY, Referrer-Policy strict-origin, CSP minimal).

### Prompts

**1.1 — Lock down `application.yaml`**

```
Open src/main/resources/application.yaml and apply these changes:

1. Replace datasource username/password with env-var lookups with no fallback:
   username: ${DB_USERNAME}
   password: ${DB_PASSWORD}
   Update README/HELP.md to mention export DB_USERNAME=root, DB_PASSWORD=root for local dev.

2. Under jpa:
   - hibernate.ddl-auto: validate
   - show-sql: false
   - properties.hibernate.format_sql: false

3. Remove debug logging for SQL: drop the org.hibernate.SQL and BasicBinder lines.

4. Under management:
   endpoints.web.exposure.include: health,info
   endpoint.health.show-details: when_authorized
   endpoint.env.show-values: never

5. Under server.error:
   include-stacktrace: never
   include-message: never
   include-exception: false

6. Add:
   spring.servlet.multipart.max-file-size: 10MB
   spring.servlet.multipart.max-request-size: 15MB

7. Leave thymeleaf.cache as default (true).

Do NOT change spring.datasource.url, spring.flyway.*, or anything related to schema location.
Verify ./mvnw test still passes.
Tag this commit as covering #1, #2, #3, #4, #5, #75.
```

**1.2 — Replace `ContentCachingFilter` with bounded wrapper**

```
Open src/main/java/com/mediconnect/interceptor/ContentCachingFilter.java.

Change every `new ContentCachingRequestWrapper(httpRequest)` to
`new ContentCachingRequestWrapper(httpRequest, 64 * 1024)`. Same for ResponseWrapper:
`new ContentCachingResponseWrapper(httpResponse)` stays (it's already bounded by servlet container),
but ensure the wrapper is only used when the route is in a known JSON-body-handling controller —
if the request Content-Type is multipart, SKIP wrapping (chain.doFilter(req, res) directly) so file
uploads don't hit the 64 KB cap.

Verify: POST /api/medical-records/1/attachment with a 1 MB PDF still works.
Tag commit: covers #74, #75.
```

**1.3 — Generic `GlobalExceptionHandler`**

```
Open src/main/java/com/mediconnect/exception/GlobalExceptionHandler.java and rewrite handlers:

- handleRuntime(RuntimeException ex) → respond {"error": "Request failed"} with HTTP 400; log full ex at WARN level server-side only.
- handleThrowable(Throwable ex) → respond {"error": "Internal error"} with HTTP 500; log at ERROR.
- Remove "type", "id", "table" fields from the response body entirely.
- For EntityNotFoundException → 404 with {"error": "Not found"} (no id/table leak).
- Keep validation errors (MethodArgumentNotValidException, ConstraintViolationException) returning the field list — those are intentional UX.

DO NOT remove the @RestControllerAdvice annotation.
DO NOT touch EntityNotFoundException constructors (other code still uses them) — just stop forwarding their fields to the client.

Verify: POST /api/auth/login with wrong password returns {"error":"Invalid credentials"} (after Phase 3) or {"error":"Request failed"} (now). No stack trace anywhere in JSON.
Tag commit: covers #71, #72, #73.
```

**1.4 — Re-enable security headers**

```
Open src/main/java/com/mediconnect/security/SecurityConfig.java.

Replace `.headers(AbstractHttpConfigurer::disable)` with:

.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
)

Keep `.csrf(disable)` for now — Phase 3 handles cookie-based auth which is the right time to flip CSRF.
Keep CORS wildcards for now — Phase 12 narrows them once frontend origin is pinned.

Verify: any GET response includes `X-Frame-Options: DENY`, `Strict-Transport-Security: ...`,
`Content-Security-Policy: ...`. Browser still loads /dashboard.
Tag commit: covers #31.
```

---

## Phase 2 — Cryptography & secret hygiene

**Covers:** vulns #16, #17, #20, #21, #79, #82, #122, #150, #153, #154, #200, #216, #217, #225, #226, #234, #247, #248
**Risk:** medium — password hashes change shape; existing seeded users need a migration plan
**Estimate:** 4–6 hours
**Depends on:** Phase 1 (env vars in place)

### Best practices

- **Passwords**: BCrypt cost 12 (Spring Security `BCryptPasswordEncoder`). Never MD5/SHA without salt.
- **Migration strategy for existing MD5 hashes**: keep a `password_algo` column (`MD5` or `BCRYPT`). On login success against MD5, immediately re-hash with BCrypt and store. Pure additive migration — no big-bang.
- **Symmetric secrets** (JWT signing, HMAC keys): load from env, minimum 32 bytes random, rotate quarterly.
- **No `SECRET` constant returned in any response or rendered in any UI.**
- `MessageDigest.isEqual()` for constant-time comparison.
- `SecureRandom` everywhere a token / password is generated.
- Sign with HMAC-SHA256 (or asymmetric ECDSA P-256 for prescriptions if you want a real medico-legal demo).

### Prompts

**2.1 — Replace MD5 with BCrypt (with backward-compat shim)**

```
Open src/main/java/com/mediconnect/security/PasswordUtils.java and src/main/java/com/mediconnect/service/AuthService.java.

In PasswordUtils:
- Add a Spring @Component BCryptPasswordEncoder field (cost 12).
- New method: String hashPassword(String raw) → encoder.encode(raw)
- New method: boolean verifyPassword(String raw, String stored)
  - if stored looks like a BCrypt hash (starts with $2a/$2b/$2y) → encoder.matches(raw, stored)
  - else (legacy MD5) → MessageDigest.isEqual(md5Hex(raw).getBytes(), stored.getBytes())
- Keep the old String hashPassword behavior signature so calling code compiles, but its body now BCrypts.

In AuthService.login:
- Replace direct hash comparison with passwordUtils.verifyPassword(rawPassword, user.getPasswordHash()).
- On successful login, if the stored hash is MD5 (detect via prefix), re-hash with BCrypt and userRepository.save the new value. Log at INFO "re-hashed user {id} from MD5 to BCrypt".

DO NOT delete the MD5 helper yet — Flyway seed migrations V10/V11/V12/V13 still insert MD5 hashes for test users.
DO NOT change RegisterRequest schema — registration already calls hashPassword which now BCrypts new users.

Run: ./mvnw test. Then manually login as patient1 / patient123, verify the row in `users` now starts with $2a or $2b.
Tag commit: covers #20, #21, #96.
```

**2.2 — Externalise JWT secret + harden parser**

```
Open src/main/java/com/mediconnect/security/JwtUtil.java.

1. Replace the SECRET constant with @Value("${app.jwt.secret}") String secret (injected).
2. Add to application.yaml under root:
   app.jwt.secret: ${JWT_SECRET}        # no fallback — server must fail to start without it
3. Drop the Arrays.copyOf zero-pad. Instead:
   - Decode the secret as base64. If decoded length < 32, throw IllegalStateException on bean init.
   - Use Keys.hmacShaKeyFor(decoded) directly.
4. Reduce EXPIRATION_MS to 30 minutes: 30L * 60 * 1000.
5. In parser: .requireAlgorithm("HS256") before parseSignedClaims.
6. Add a refresh-token method that mints a 7-day signed refresh token bound to userId; AuthService.login returns both. (If you want to skip refresh tokens to limit scope, leave a TODO and set access-token to 60 min for now.)

Update HELP.md: "Set JWT_SECRET to a base64 string of at least 32 bytes. Generate: openssl rand -base64 48".

DO NOT change the token shape (still {sub, iat, exp, role, userId}).
DO NOT modify JwtAuthenticationFilter in this commit — Phase 3 owns that file.

Verify: app starts only with JWT_SECRET exported; existing logged-in users get 401 once their old token expires (acceptable).
Tag commit: covers #16, #17, #18, #19.
```

**2.3 — Strip plaintext key leaks from prescription/lab signing**

```
Open src/main/java/com/mediconnect/service/PrescriptionSigner.java, DoctorPrescribingService.java,
DoctorLabService.java, and src/main/java/com/mediconnect/dto/PrescriptionSignatureDto.java.

1. PrescriptionSigner: replace the `SECRET` constant with @Value("${app.prescription.sign.key}").
   Change md5Signature → hmacSignature using Mac.getInstance("HmacSHA256") and the env-loaded key.
   Rename method, store result in signature_hmac_sha256 column (Phase 9 schema change).
2. DoctorPrescribingService.sign:
   - Remove .signingKey(PrescriptionSigner.SECRET) from the response builder.
   - Keep .signatureMd5 field returning HMAC-SHA256 hex for now (Phase 9 renames the column).
3. PrescriptionSignatureDto: delete the signingKey field entirely. Frontend coordination: search
   PrescriptionSigner.tsx for signingKey and replace the <code> block with a "Signed" check icon
   plus the hex signature only.
4. Same treatment for DoctorLabService: replace LAB_SIGN_KEY constant with env @Value, HMAC-SHA256
   in place of MD5. Do NOT return the key. Update LabOrderDto / order-detail UI accordingly.

application.yaml adds:
  app.prescription.sign.key: ${PRESC_SIGN_KEY}
  app.lab.sign.key:          ${LAB_SIGN_KEY}

Frontend (mediconnect-frontend):
- components/doctor/PrescriptionSigner.tsx: remove the signingKey display panel.
- DoctorLabsPage.tsx: remove any "signing key" display.

Tag commit: covers #200, #216, #217, #225, #226, #234.
```

**2.4 — Replace AI API key constant**

```
Open src/main/java/com/mediconnect/service/DoctorAIService.java.

1. Replace AI_GATEWAY_URL, AI_API_KEY, AI_MODEL public-static constants with @Value fields.
2. application.yaml adds:
     app.ai.gateway-url: ${AI_GATEWAY_URL:}
     app.ai.api-key:     ${AI_API_KEY:}
     app.ai.model:       ${AI_MODEL:}
3. In modelInfo(): return only { gatewayUrl, model } and a redacted "apiKey":"[REDACTED]" placeholder.
   The frontend AIModelInfoCard (exported from AIAssistantPanel.tsx) is updated to never render the apiKey
   value — replace <code>{info.apiKey}</code> with the literal text "*** redacted ***".
4. In postJsonAndReadResponse: only attach the Authorization header IF the destination URL host
   matches the configured app.ai.gateway-url host. Otherwise omit the header.

DO NOT remove the suggest / summarize-record endpoints — Phase 8 hardens their SSRF surface.
Tag commit: covers #247, #248, #255.
```

**2.5 — Stop returning password hash and use SecureRandom for generated passwords**

```
Open src/main/java/com/mediconnect/dto/UserDto.java and search for passwordHash references in services.

1. Add @JsonIgnore on UserDto.passwordHash so it's stripped from API responses.
2. In UserService.toDto, AdminUserService.toDto, etc. STOP calling .passwordHash(...) on the builder —
   field stays null in the response.
3. AdminUserService.generateRandomPassword: replace `new Random()` with `new SecureRandom()`.
   Use a 16-char password, alphabet [A-Za-z0-9!@#$%^&*].
4. resetPassword: still returns the newPassword in the response body (clinical workflow needs it for the
   one-time print), but DO NOT log it via ContentCachingFilter — Phase 11 fixes the logging side.
   For now, add @JsonProperty access=WRITE_ONLY guarding plus a comment that this is the ONE place
   plaintext leaves the system intentionally.

Frontend coordination (mediconnect-frontend):
- DashboardPage.tsx admin table: drop the "passwordHash" <th> + <td>. Replace with a "Last login" column
  or just remove the column.
- AdminPage.tsx user table: drop the passwordHash column and the copyHash button.
- AdminUserDetailPage.tsx: drop the passwordHash row in the detail card.
- AuthContext.tsx: the user object stored in localStorage will simply no longer have passwordHash —
  no code change needed in the consumer.

Run frontend `npx tsc -b --noEmit` to catch type errors.
Tag commit: covers #11, #37, #79, #82, #122, #154.
```

---

## Phase 3 — Authentication & JWT hardening

**Covers:** vulns #19, #23, #24, #25, #26, #27, #33, #34, #35, #77, #80, #81, #85, #210, #230
**Risk:** medium-high — existing localStorage tokens become invalid; rate limiting can break tests
**Estimate:** 3–4 hours
**Depends on:** Phase 2

### Best practices

- **JWT in httpOnly + Secure + SameSite=Strict cookie**, not in JSON body or localStorage.
- **Always validate signature + algorithm + expiry**; never swallow parser exceptions.
- **Generic auth error messages**: `"Invalid credentials"` for register-username-taken, email-taken, login-bad-pass, login-no-user, account-locked.
- **Rate limit** /login + /register: 5 attempts / 15 min / IP via a Bucket4j or simple in-memory token bucket.
- **Reject alg=none JWTs explicitly.**
- **Bean Validation** on RegisterRequest (`@Email`, `@Size(min=12)`, `@Pattern` for complexity, role NOT in client body — always assign PATIENT).

### Prompts

**3.1 — Lock the JWT filter**

```
Open src/main/java/com/mediconnect/security/JwtAuthenticationFilter.java.

1. Delete the SKIP_EXPIRY_PATHS list and the entire skipExpiry code path. Every request validates
   signature + expiry, no exceptions.
2. Replace `catch (Exception e) { }` with:
     catch (Exception e) {
         response.setStatus(HttpStatus.UNAUTHORIZED.value());
         response.setContentType("application/json");
         response.getWriter().write("{\"error\":\"Unauthorized\"}");
         return;
     }
3. Read the token from `Cookie: token=...` first; only fall back to Authorization header if cookie
   absent (gives backward compat for tools/Postman until frontend migrates).

Verify: app boots; existing tokens that aren't expired still work; expired tokens always 401 even on
/api/public/* paths (those routes should be unauthenticated entirely if they need to stay open — give
them to SecurityConfig in Phase 4).

Tag commit: covers #33, #34.
```

**3.2 — Generic auth errors + Bean Validation**

```
Open src/main/java/com/mediconnect/service/AuthService.java, CustomUserDetailsService.java,
RegisterRequest.java, LoginRequest.java.

1. RegisterRequest:
   - @NotBlank + @Size(min=3,max=40) on username
   - @Email + @NotBlank on email
   - @NotBlank + @Size(min=12) + @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$") on password
   - DELETE the `role` field entirely. Registration always creates PATIENT accounts.
2. AuthController.register: add @Valid on the request body so the validation kicks in. Validation
   errors return 400 with field map (let GlobalExceptionHandler from Phase 1 handle that).
3. AuthService.register:
   - Single error message "Registration failed" returned in JSON, regardless of whether username or
     email is taken. Log the real reason at INFO server-side.
   - Always assign Role.PATIENT on .role(...).
4. AuthService.login: catch every branch (no user / wrong password / locked / inactive) → throw a
   single new BadCredentialsException with message "Invalid credentials". DO NOT include the timestamp
   or any other detail. GlobalExceptionHandler maps this to HTTP 401 with {"error":"Invalid credentials"}.
5. CustomUserDetailsService.loadUserByUsername: on miss, throw `new UsernameNotFoundException("Invalid credentials")` (constant message, no username leak).
6. Track failed attempts in `users.failed_login_attempts` as before, lock at 5 with 15 min cooldown.
   Increment from inside the catch block of step 4.

DO NOT remove user enumeration via timing in this prompt — that's a separate hardening pass. Get the
messages right first.

Tag commit: covers #23, #24, #25, #35.
```

**3.3 — Rate limit /login + /register**

```
Add a new file src/main/java/com/mediconnect/security/RateLimitFilter.java:

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String uri = req.getRequestURI();
        if (!uri.startsWith("/api/auth/login") && !uri.startsWith("/api/auth/register")) {
            chain.doFilter(req, res); return;
        }
        String key = req.getRemoteAddr() + ":" + uri;
        Bucket bucket = buckets.computeIfAbsent(key, k ->
            Bucket.builder().addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15)))).build());
        if (bucket.tryConsume(1)) chain.doFilter(req, res);
        else { res.setStatus(429); res.getWriter().write("{\"error\":\"Too many requests\"}"); }
    }
}

Add bucket4j dependency to pom.xml: com.bucket4j:bucket4j-core:8.10.1.

Wire into SecurityConfig only for the auth endpoints — don't blanket /api/**.

DO NOT use the real client IP from X-Forwarded-For until Phase 11 fixes that. For now, RemoteAddr is fine.

Tag commit: covers #27.
```

**3.4 — JWT to httpOnly cookie (backend half)**

```
Open src/main/java/com/mediconnect/controller/AuthController.java.

1. In login()/register() success path, set a Cookie:
     ResponseCookie cookie = ResponseCookie.from("token", token)
         .httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(Duration.ofMinutes(30)).build();
     response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
2. Keep the JSON body returning `{user: {...}}` but DELETE the token field from the body.
3. Add POST /api/auth/logout that clears the cookie (maxAge=0).
4. Add @PreAuthorize on the existing /api/auth/me endpoint (or create it if missing) — returns the
   authenticated UserDto. Frontend uses this on app boot instead of decoding the JWT.

Re-enable CSRF for cookie-based mutations:
SecurityConfig:
- .csrf(csrf -> csrf
     .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
     .ignoringRequestMatchers("/api/auth/login","/api/auth/register"))

DO NOT touch the frontend yet — that's the next prompt. Backend should accept BOTH cookie-token AND
Authorization-header-token until the frontend migrates (JwtAuthenticationFilter from 3.1 already does this).

Tag commit: covers #26, #76, #77, #80, #81, #85.
```

**3.5 — Frontend: cookie-based auth**

```
In mediconnect-frontend:

1. src/api/axiosInstance.ts:
   - Set defaults.withCredentials = true so cookies ride along.
   - REMOVE the request interceptor that injects Authorization from localStorage.
   - Add a response interceptor: on 401, redirect to /login.
2. src/auth/AuthContext.tsx:
   - Delete decodeJwtPayload entirely.
   - On app boot, call GET /api/auth/me to populate the user state. No localStorage read for token/user.
   - login(): call POST /api/auth/login. Don't expect a token field. On success call /me, store user
     in React state ONLY (no localStorage.setItem('user', ...)).
   - logout(): call POST /api/auth/logout, clear state.
3. Search the repo for `localStorage.getItem('token')` / `localStorage.setItem('token'`/`'user'` and
   DELETE every usage. Replace with `useAuth().user`.
4. src/pages/DashboardPage.tsx: remove the Token Inspector widget block entirely.
5. src/pages/ProfilePage.tsx: rebuild the export URL flow as POST /api/users/{id}/export with the
   server returning a one-time signed URL (out of scope of this prompt — just remove the token query
   string version and link to a 501-Not-Implemented placeholder for now).
6. src/components/admin/ImpersonateBanner.tsx: keep the banner, but read the impersonation flag from
   a backend GET /api/auth/me?includeImpersonation=true response field instead of localStorage.

Verify: log in, F12 → Application → Cookies sees `token` httpOnly. Local storage has no `token` / `user`.
Refresh → still logged in. Logout → cookie cleared.

Tag commit: covers #76, #77, #78, #79, #81, #83, #85.
```

**3.6 — Kill alg=none acceptors**

```
Open src/main/java/com/mediconnect/security/JwtNoneVerifier.java.

Either delete the class entirely (preferred) or rewrite extractSubjectUnsafe to verify a real signed JWT:
  - parse with the same JwtUtil verifier; reject alg!=HS256
  - return parsedClaims.getSubject()

Search for every call site (DoctorNoteService.coSign, DoctorPrescribingService.coSign). Update them
to use the standard JwtUtil parser.

Frontend (mediconnect-frontend): the co-sign dialog needs to send a real signed JWT, not a hand-crafted
one. Update the form description text to say "Paste a valid signed JWT from another doctor's session".

Tag commit: covers #210, #230.
```

---

## Phase 4 — Authorization (Broken Access Control)

**Covers:** all A01 vulns (most of the 101 in this category)
**Risk:** high — easy to lock yourself out, easy to break smoke tests
**Estimate:** 6–8 hours, do in sub-batches
**Depends on:** Phase 3 (JWT validation must be reliable first)

### Best practices

- **Server is the only source of truth** — never trust client-supplied IDs that imply ownership.
- Use `@PreAuthorize("hasRole('ADMIN')")` for admin endpoints.
- Use `@PreAuthorize("@authz.canViewUser(authentication, #id)")` SpEL beans for ownership checks.
- Build an `AuthzService` bean with one method per resource (canViewUser, canViewAppointment, canViewMedicalRecord, ...). Single place to audit.
- Replace any path that takes a `userId` / `patientId` from the URL where the caller "should be" that user with code that reads the principal from `SecurityContextHolder`.
- HTTP method semantics: `DELETE /api/users/{id}` not `GET /api/users/delete/{id}`.

### Prompts

**4.1 — Build the AuthzService**

```
Create src/main/java/com/mediconnect/security/AuthzService.java:

@Service("authz")
public class AuthzService {
    private final UserRepository users;
    private final AppointmentRepository appts;
    private final MedicalRecordRepository records;
    private final MessageRepository messages;
    private final PrescriptionRepository prescriptions;
    private final LabResultRepository labs;
    private final PatientRepository patients;

    public boolean isSelf(Authentication auth, Long userId) {
        return userIdOf(auth).equals(userId);
    }
    public boolean isAdmin(Authentication auth) { return hasRole(auth, "ADMIN"); }
    public boolean isDoctor(Authentication auth) { return hasRole(auth, "DOCTOR"); }

    public boolean canViewAppointment(Authentication auth, Long apptId) {
        Long uid = userIdOf(auth);
        return isAdmin(auth) || appts.findById(apptId).map(a ->
            a.getPatient().getUser().getId().equals(uid) || a.getDoctor().getUser().getId().equals(uid)
        ).orElse(false);
    }

    public boolean canViewMedicalRecord(Authentication auth, Long recordId) { ... similar ... }
    public boolean canViewMessage(Authentication auth, Long messageId) { ... sender or receiver ... }
    public boolean canViewPatientChart(Authentication auth, Long patientId) { ... admin OR a doctor with a past/active appointment with that patient ... }

    private Long userIdOf(Authentication auth) { return ((UserPrincipal) auth.getPrincipal()).getUser().getId(); }
    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}

Add @EnableMethodSecurity to SecurityConfig.

DO NOT yet wire it into controllers — next prompts do that controller-by-controller.

Tag commit: scaffolding (no vuln numbers yet).
```

**4.2 — Lock SecurityConfig.requestMatchers**

```
Open SecurityConfig.java. Replace the chain:

.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/login","/api/auth/register","/api/auth/logout").permitAll()
    .requestMatchers("/api/actuator/health").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/doctor/**").hasAnyRole("DOCTOR","ADMIN")
    .anyRequest().authenticated())

DELETE every other permitAll() including the `/api/refills/**` one — refills need authenticated users.

Run smoke list from §0.2. Expect: most endpoints fail with 401/403 until controllers add their own
@PreAuthorize. THAT IS FINE — next prompts wire them in.

Tag commit: covers #22, #28, #29, #62, #139, #145, #191.
```

**4.3 — Wire @PreAuthorize on User / Patient / Stats controllers**

```
Open UserController.java, PatientController.java, StatsController.java.

UserController:
- GET /                  @PreAuthorize("hasRole('ADMIN')")
- GET /{id}              @PreAuthorize("@authz.isSelf(authentication,#id) or @authz.isAdmin(authentication)")
- PUT /{id}/role         @PreAuthorize("hasRole('ADMIN')")
- PUT /{id}              @PreAuthorize("@authz.isSelf(authentication,#id) or @authz.isAdmin(authentication)")
- Change /delete/{id} from @GetMapping to @DeleteMapping("/{id}") + @PreAuthorize("hasRole('ADMIN')").
  Frontend Sidebar/UserList: search for any link to /api/users/delete and change to api.delete('/users/{id}').

PatientController:
- GET /by-user/{userId}  @PreAuthorize("@authz.isSelf(authentication,#userId) or @authz.isAdmin(authentication) or @authz.isDoctor(authentication)")
  (Doctors may need to view by user id for the chart; tighten further if not needed.)

StatsController:
- All /summary, /charts, /recent  @PreAuthorize("hasRole('ADMIN')")  for now. Patient dashboard
  needs a separate, scoped endpoint — add GET /api/stats/me later if patient dashboard regresses.

Also UserService.updateRole: change signature to accept the caller principal and refuse if user.role is
already ADMIN and the caller is not ADMIN (defense in depth on top of @PreAuthorize).

Tag commit: covers #36, #38, #39, #40, #89, #90, #91, #95.
```

**4.4 — Appointment / MedicalRecord / LabResult / Message / Prescription controllers**

```
Apply the same @PreAuthorize pattern to:

AppointmentController:
- GET /{id}        canViewAppointment(#id)
- PUT /{id}        canViewAppointment(#id) AND (admin OR doctor only — patients can't edit notes)
- PUT /{id}/status canEditAppointmentStatus(#id) (admin OR the assigned doctor)
- GET /{id}/pdf    canViewAppointment(#id)
- GET / (list)     hasAnyRole(ADMIN, DOCTOR) — patients use a new /api/appointments/me endpoint that
                   filters server-side by the JWT subject. Add that endpoint.

MedicalRecordController:
- GET /            hasAnyRole(ADMIN, DOCTOR)
- GET /{id}        canViewMedicalRecord(#id)
- POST /           hasAnyRole(DOCTOR, ADMIN) + service-level check that the doctor has an appointment with the patient
- PUT /{id}        canEditMedicalRecord(#id) (the doctor who created it OR ADMIN)
- attachment GET   canViewMedicalRecord(#id)
- attachment POST  canEditMedicalRecord(#id)
  ALSO: remove the `filePath` query param entirely. Always read from the record's stored attachmentPath.

LabResultController:
- GET /search      hasAnyRole(DOCTOR, LAB_TECH, ADMIN); patientId becomes REQUIRED for non-admin callers
                   and the service rejects callers who aren't a doctor with a relationship to that patient.
                   Patients use a new GET /api/lab-results/me which filters server-side.
- GET /{id}        canViewLabResult(#id)
- POST/PUT         hasRole(LAB_TECH) + service-level relation check

MessageController:
- GET /conversation/{userId}  delete the viewerId QUERY PARAM. Read viewer from JWT.
                              @PreAuthorize("@authz.isSelf(authentication,#userId) or @authz.isAdmin(authentication)") — well, no — verify the JWT subject is one of the two participants.
- POST /           service-level: senderId = authenticated subject (ignore body). Frontend Compose form
                   should stop sending senderId.
- DELETE /{id}     canDeleteMessage(#id) — only sender, or admin.
- PATCH /{id}/read canViewMessage(#id) — only receiver.
- GET /conversations Remove userId query param. Use JWT.

PrescriptionController:
- POST /           hasAnyRole(DOCTOR, ADMIN); doctor relationship enforced
- GET /{id}        canViewPrescription(#id)
- PUT /{id}/dispense  hasRole(PHARMACIST); pharmacistId from JWT, ignore body.
- PUT /{id}/status    hasAnyRole(PHARMACIST, DOCTOR, ADMIN) + state-machine check (Phase 10 owns the
                      state machine).

Tag commit: covers #42, #43, #45, #52, #56, #57, #58, #61, #92, #93, #94, #102–#108, #115–#118, #126.
```

**4.5 — Admin controllers**

```
Apply hasRole('ADMIN') to ALL of:

AdminUserController, AdminAuditController, AdminOpsController, AdminClinicalController,
AdminStaffController, AdminBroadcastController.

EXCEPT:
- POST /admin/users/{id}/impersonate: hasRole('ADMIN') AND require a header `X-Impersonation-Reason`
  with at least 10 chars. Write an audit row capturing the actor + reason BEFORE minting the JWT.
  Add an `impersonated_by` claim to the impersonation token; UI displays the banner from that claim,
  not from a localStorage flag.

DO NOT delete any admin endpoint yet (Phase 13 retires the truly dangerous ones).

Tag commit: covers #62–#65, #145–#157, #173–#178, #179–#190.
```

**4.6 — Doctor controllers + frontend route guards**

```
Backend:
- DoctorRosterController, DoctorNoteController, DoctorLabController, DoctorPrescribingController,
  DoctorSessionController, DoctorAIController, DoctorReferralController:
  - @RequestMapping endpoints all get @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')").
  - Patient-scoped endpoints (chart, timeline) also add @authz.canViewPatientChart(#id).
  - Module B/C/D/E/F mutations: doctor must own the row (note created_by, lab order created_by,
    prescription doctor_id, telemedicine session doctor_id, referral toDoctor or fromDoctor).
  - Add `createdBy`/`fromDoctorUserId` columns where missing (additive migrations).

Frontend (mediconnect-frontend):
- src/auth/ProtectedRoute.tsx: keep, but `requiredRole` becomes the canonical check (already supports it).
- src/auth/AdminRoute.tsx: WRAP <ProtectedRoute requiredRole="ADMIN">{children}</ProtectedRoute>.
- src/auth/DoctorRoute.tsx: same with DOCTOR.
- App.tsx: ensure /staff, /admin/*, /doctor/* route trees use the right wrapper.
- Sidebar.tsx: keep the role-based dashboardPath mapping, but the server gates so a wrong choice
  just shows an empty page rather than data.

Tag commit: covers #155, #156, #157, #191, #192, #193, #197, #201, #202.
```

---

## Phase 5 — SQL injection & input validation

**Covers:** #41, #51, #158, #165, #168, #194
**Risk:** medium — easy to introduce parsing bugs in WHERE-clause builders
**Estimate:** 2–3 hours
**Depends on:** none

### Best practices

- `NamedParameterJdbcTemplate` for every dynamic SQL. Never `String + String`.
- `EntityManager.createNativeQuery(sql).setParameter(...)` instead of inline.
- For LIKE: `setParameter("q", "%" + sanitize(q) + "%")` where `sanitize` strips `%` and `_` (or escape).
- For dynamic AND clauses: use a `MapSqlParameterSource` and append `AND col = :col` strings; binding is per-param.
- **Prefer JPA Criteria API** over raw SQL when feasible — type-safe, no concat risk.

### Prompts

**5.1 — Parameterise every searchSomething service**

```
Refactor every "raw SQL concatenation" service to use NamedParameterJdbcTemplate or Criteria API:

- AppointmentService.searchAppointments
- LabResultService.searchLabResults
- AdminAuditService.search
- AdminOpsService.getDashboard (since → bind as a java.time.LocalDateTime parameter, NOT concat)
- DoctorRosterService.listPatients

Pattern:

  String sql = """
      SELECT ... FROM appointments a JOIN doctors d ON a.doctor_id = d.id JOIN users u ON d.user_id = u.id
      WHERE 1=1
      """ +
      (doctorName != null ? " AND u.last_name LIKE :doctorName" : "") +
      (status != null ? " AND a.status = :status" : "");
  Map<String,Object> params = new HashMap<>();
  if (doctorName != null) params.put("doctorName", "%" + doctorName + "%");
  if (status != null) params.put("status", status);
  return namedJdbc.query(sql, params, rowMapper);

For numeric/enum params, validate before binding (e.g. EnumUtils.isValidEnum for status).

DO NOT change the response shape; keep DTO fields identical.

Tag commit: covers #41, #51, #158, #165, #194.
```

**5.2 — Retire `runSql`, `backup`, `dashboard since`**

```
Open AdminOpsController and AdminOpsService.

Option A (recommended): delete /maintenance/run-sql entirely. UI: AdminOpsPage.tsx RunSqlConsole — replace
with a card that reads "Direct SQL execution removed. Use the Audit Logs export or contact engineering."

Option B (keep for demo flexibility): wrap the existing logic in @PreAuthorize("hasRole('SUPER_ADMIN')")
and add a new SUPER_ADMIN role nobody has by default.

For backup: replace ProcessBuilder("/bin/sh","-c","mysqldump "+dbName) with a hardcoded `mysqldump --no-create-db mediconnect_db` invocation (no dbName param at all). Pin the binary path. Don't accept any query string.

For runtime config: delete PUT /maintenance/config/{key}. It can't be made safe.

Tag commit: covers #167, #168, #170.
```

---

## Phase 6 — File upload + path traversal

**Covers:** #46–#50, #53, #54, #181, #183, #218–#221, #240, #241
**Risk:** medium — uploads are a regression magnet
**Estimate:** 3–4 hours

### Best practices

- Generate filename server-side: `UUID.randomUUID() + safeExt(original)`. Never trust client filename.
- Whitelist extensions: `pdf, png, jpg, jpeg, dicom`. Reject all else.
- Validate magic bytes (Apache Tika `Tika().detect(stream)`).
- Cap size at 10 MB via `@RequestPart` + `maxFileSize`.
- Resolve final path: `uploadDir.resolve(safeName).normalize()` and assert `startsWith(uploadDir)`.
- Serve via `GET /api/.../{id}/file` reading the stored path from DB — never accept a `filePath` query param.
- `Content-Disposition: attachment; filename="..."` always. Inline rendering belongs to a separate signed-URL flow with strict mime checks.

### Prompts

**6.1 — Central FileStorageService**

```
Create src/main/java/com/mediconnect/service/FileStorageService.java:

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED = Set.of("pdf","png","jpg","jpeg","dcm","svg");
    private static final Set<String> ALLOWED_MIME = Set.of(
        "application/pdf","image/png","image/jpeg","image/svg+xml","application/dicom");
    private final Path baseDir;
    private final Tika tika = new Tika();

    public FileStorageService(@Value("${app.upload.dir}") String dir) throws IOException {
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.baseDir);
    }

    public StoredFile store(MultipartFile file, String subdir) throws IOException {
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("file too large");
        String mime = tika.detect(file.getInputStream());
        if (!ALLOWED_MIME.contains(mime)) throw new IllegalArgumentException("unsupported type");
        String ext = mime.equals("application/pdf") ? "pdf" : mime.substring(mime.indexOf('/')+1).replace("svg+xml","svg");
        if (!ALLOWED.contains(ext)) throw new IllegalArgumentException("unsupported extension");
        String safeName = UUID.randomUUID() + "." + ext;
        Path target = baseDir.resolve(subdir).resolve(safeName).normalize();
        if (!target.startsWith(baseDir)) throw new SecurityException("traversal");
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.CREATE_NEW);
        }
        String sha256 = sha256Hex(target);
        return new StoredFile(target.toString(), safeName, mime, file.getSize(), sha256);
    }

    public Resource load(String storedPath) throws IOException {
        Path p = Paths.get(storedPath).normalize();
        if (!p.startsWith(baseDir)) throw new SecurityException("traversal");
        return new UrlResource(p.toUri());
    }
    // ... sha256 helper ...
    public record StoredFile(String absPath, String filename, String mime, long size, String contentHash) {}
}

Migrate every uploader to use it: MedicalRecordService, LabResultService, AdminStaffService,
DoctorLabService, DoctorSessionService.attachRecording.

After upload, persist the contentHash to the entity (Phase 9 schema migration adds the column).

Tag commit: covers #46, #47, #53, #181, #218, #220, #221, #240, #241.
```

**6.2 — Kill the `filePath` query param**

```
For every download endpoint that accepts `filePath` from the client, drop that parameter.
Read attachmentPath from the database entity:

  @GetMapping("/{id}/attachment")
  public ResponseEntity<Resource> download(@PathVariable Long id) {
      // @PreAuthorize already enforced
      MedicalRecord rec = service.findById(id);
      Resource r = storage.load(rec.getAttachmentPath());
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+rec.getOriginalFilename()+"\"")
          .body(r);
  }

Also remove the "path" field from any upload response — only return `{id, filename, contentHash}`.

DO NOT serve image/svg+xml inline. Even when storage says it's an image, force attachment Content-Disposition
for SVG; allow inline only for png/jpg.

Frontend (mediconnect-frontend):
- DoctorLabsPage.tsx inline image viewer: only render <img> for content-type png/jpg/jpeg. Otherwise
  show a "Download" button. Remove the <iframe> fallback entirely.

Tag commit: covers #48, #49, #50, #54, #114, #219.
```

---

## Phase 7 — XSS sanitisation

**Covers:** #9, #14, #55, #84, #100, #143, #144, #161, #164, #171, #183, #185, #188, #189, #199, #203, #207, #211, #215, #251, #252
**Risk:** medium — frontend has many sites
**Estimate:** 3–4 hours

### Best practices

- **Never `dangerouslySetInnerHTML` user-controlled content.** If you need rich text, sanitise with DOMPurify and a strict whitelist (`a, b, em, strong, p, br, ul, ol, li`).
- Treat audit-log `details`, message `content`, broadcast `body`, note `renderedHtml` as text-by-default. Render `<pre>{value}</pre>` or use `react-markdown` with `disallowedElements={['script','iframe','img','svg']}`.
- Backend: still sanitise on write via `Jsoup.clean(content, Safelist.basic())` — defense in depth.

### Prompts

**7.1 — Backend sanitisation on save**

```
Add a SanitizerService that wraps Jsoup:

@Service
public class SanitizerService {
    private static final Safelist SAFE = Safelist.basic().preserveRelativeLinks(true);
    public String clean(String html) { return html == null ? null : Jsoup.clean(html, SAFE); }
    public String text(String s) { return s == null ? null : Jsoup.clean(s, Safelist.none()); }
}

Apply in:
- MessageService.send: content = sanitizer.clean(dto.getContent());
- AdminBroadcastService.broadcast: subject = sanitizer.text(subject); html = sanitizer.clean(html);
- DoctorRosterService.starPatient: note = sanitizer.text(note);  // stars are plain text
- DoctorNoteService: do NOT sanitise renderedHtml because the entire SSTI/XXE chain is being killed in
  Phase 8 — note bodies will be plain text or sanitised markdown.
- AdminAuditService.exportLogs: escape CSV via OpenCSV / Apache Commons CSV (proper escaping for quotes,
  formula-prefix neutralisation via leading `'`).
- AdminAuditService.toXmlElement: use a proper XML library (StAX or JAXB) for output construction.

Add `org.jsoup:jsoup:1.18.1` and `org.apache.commons:commons-csv:1.11.0` to pom.xml.

Tag commit: covers #9, #14, #55, #185, #199.
```

**7.2 — Frontend: replace every dangerouslySetInnerHTML**

```
In mediconnect-frontend, search for `dangerouslySetInnerHTML` and replace each occurrence:

- MessagesPage.tsx (msg.content) → <p className="whitespace-pre-wrap">{msg.content}</p>
- AdminLogDetailPage.tsx (data.details) → <pre className="whitespace-pre-wrap font-mono text-xs">{data.details}</pre>
- AdminOpsPage.tsx (run-sql cells) → <td>{String(cell ?? '')}</td>
- AdminBroadcastPage.tsx preview → render markdown via `react-markdown` with disallowedElements `['script','iframe','img','svg','link','style']`. Add `remark-gfm` for tables.
- AdminOnboardingPage.tsx (uploadedFilename) → {lastResult.uploadedFilename}
- DoctorPatientsPage.tsx (starNote) → {p.starNote}
- DoctorNoteDetailPage.tsx (renderedHtml) → <pre className="whitespace-pre-wrap">{note.renderedHtml}</pre>
- DoctorLabsPage.tsx (catalogueResponse) → <pre>{o.catalogueResponse}</pre>
- AIAssistantPanel.tsx (result.response) → <pre>{result.response}</pre>
- ReferralBundleViewer.tsx (previewSummary) → {referral.previewSummary}
- AdminPage.tsx + ProfilePage.tsx + RefillsPage.tsx (failureReason) → <pre>{r.failureReason}</pre>

If a place genuinely needs rendered markdown (admin broadcasts) — use react-markdown with the disallowedElements above. Install `react-markdown` + `remark-gfm`.

Tag commit: covers #84, #100, #143, #144, #161, #164, #171, #183, #189, #207, #215, #251, #252.
```

---

## Phase 8 — SSRF, SSTI, XXE, deserialisation

**Covers:** #163, #204–#206, #211–#214, #220, #228, #229, #240, #245, #246, #249, #250
**Risk:** high — kills entire features; coordinate with frontend
**Estimate:** 4–6 hours

### Best practices

- **No untrusted URL fetched server-side without an allowlist.** Validate scheme (`https` only), DNS-resolve and reject RFC1918 / link-local / metadata IPs.
- **No string-based template engines on user-supplied templates.** Freemarker `renderInline` → delete. `renderByName` → only with a fixed allowlist of template names that ship with the app.
- **No XML parser without `disallow-doctype-decl`.** Apache `XMLConstants.FEATURE_SECURE_PROCESSING=true`, `external-general-entities=false`, `external-parameter-entities=false`, `load-external-dtd=false`.
- **No Nashorn / no script engines on user input.** Period.
- **No Java deserialisation of untrusted bytes.** Migrate `ReferralBundle` to a JSON DTO; never call `ObjectInputStream.readObject()` on caller-supplied data. If you must, install `ObjectInputFilter.allowFilter("com.mediconnect.dto.*;!*")` and a max depth.

### Prompts

**8.1 — Build SafeUrlFetcher**

```
Create src/main/java/com/mediconnect/service/SafeUrlFetcher.java:

@Service
public class SafeUrlFetcher {
    private final List<String> allowedHosts;  // from app.fetch.allowed-hosts (comma-separated)

    public byte[] fetch(String urlStr) throws IOException {
        URI uri = URI.create(urlStr);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("scheme");
        String host = uri.getHost();
        if (host == null) throw new IllegalArgumentException("host");
        InetAddress addr = InetAddress.getByName(host);
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress() ||
            addr.isSiteLocalAddress() || isMetadataIp(addr)) throw new SecurityException("blocked ip");
        if (!allowedHosts.contains(host)) throw new SecurityException("host not allowed");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(3000); conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            return in.readNBytes(1024 * 1024);  // 1 MB cap
        }
    }
    private boolean isMetadataIp(InetAddress a) {
        String s = a.getHostAddress();
        return s.equals("169.254.169.254") || s.equals("100.100.100.200") || s.equals("fd00:ec2::254");
    }
}

application.yaml: app.fetch.allowed-hosts: drugs.example.com,api.openai.com,...

Replace ExternalCatalogueClient.fetchBytes and every URLConnection.openConnection() call site in:
- DoctorLabService (lab-orders fetch + import-url)
- DoctorPrescribingService (pharmacy callback POST — convert to a known internal pharmacy URL only)
- DoctorSessionService (recording attach)
- DoctorAIService (suggest, summarize-record)

Delete ExternalCatalogueClient entirely; everyone uses SafeUrlFetcher.

Tag commit: covers #214, #220, #228, #240, #249.
```

**8.2 — Lock down Freemarker + delete SSTI**

```
Open src/main/java/com/mediconnect/service/TemplateRenderer.java and DoctorNoteService.java.

TemplateRenderer:
- DELETE renderInline(String, Map) entirely. Inline-template feature is dead.
- renderByName: keep, but cfg.setTemplateLoader to a ClassPathTemplateLoader rooted at
  /templates/notes/. Provide a hardcoded allowlist Set<String> ALLOWED = Set.of("soap","progress","discharge").
  Reject any name not in the set.
- cfg.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER) — blocks ?new() to instantiate Execute.
- cfg.setAPIBuiltinEnabled(false), cfg.setLogTemplateExceptions(false), cfg.setWrapUncheckedExceptions(true).
- cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER).
- Remove the <pre class=render-error> swallow; let the controller respond 400 on render failure.

DoctorNoteService.create:
- Drop the inline-template code path (templateBody arg).
- Only accept templateName from the allowlist + a JSON data map.

Frontend (mediconnect-frontend):
- NoteEditor.tsx: remove the "Inline Freemarker template" checkbox + textarea. Keep only the Select dropdown
  for templateName, populated from a hardcoded constant list matching backend.

Tag commit: covers #204, #205, #211, #212.
```

**8.3 — Harden XML parsing (kill XXE)**

```
Open every DocumentBuilderFactory usage in:
- AdminAuditService.renderXmlWithTemplate
- DoctorNoteService.importXml

Apply hardening:

DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
dbf.setXIncludeAware(false);
dbf.setExpandEntityReferences(false);

Same pattern for any SAXParserFactory, XMLInputFactory, TransformerFactory if used.

Tag commit: covers #163, #206, #213.
```

**8.4 — Remove Nashorn entirely**

```
Open src/main/java/com/mediconnect/service/DoctorPrescribingService.java#drugInteractions.

1. Delete the ScriptEngineManager / js.eval() block.
2. Replace the drug interactions check with a hardcoded local lookup (JSON resource file at
   src/main/resources/drug-interactions.json with [{drug, interactsWith, severity}] entries).
3. Service signature stays the same so the UI still works; it just no longer touches a script engine.

Remove from pom.xml: org.openjdk.nashorn:nashorn-core.

Frontend (mediconnect-frontend):
- DrugInteractionPanel.tsx: remove the catalogueUrl input field. The check is now local; submit just
  patientId + medication list.

Tag commit: covers #229.
```

**8.5 — Replace ReferralBundle deserialisation**

```
Open src/main/java/com/mediconnect/dto/ReferralBundle.java and DoctorReferralService.java.

Two paths — pick one. Recommended: option A.

OPTION A (preferred): replace binary deserialisation with JSON.
- Change ReferralBundle to a plain @Data class WITHOUT implementing Serializable. Remove the readObject hook + cmd field.
- DoctorReferralService.decodeBundle: stop calling ObjectInputStream. Instead, the bundlePayload column
  stores a JSON string. decodeBundle = objectMapper.readValue(payload, ReferralBundle.class).
- Migration V25__referral_payload_json.sql: ALTER TABLE referrals ADD COLUMN bundle_json TEXT;
  Backfill bundle_json from bundle_payload via a one-time Java migration (Flyway Java migration
  R__migrate_referral_payload.java that reads the base64+deser, repacks as JSON, then drops bundle_payload).
- Validate the JSON via Bean Validation: @NotNull on diagnosis, etc.

OPTION B (only if you must keep binary): install ObjectInputFilter:
  ObjectInputStream ois = new ObjectInputStream(...);
  ois.setObjectInputFilter(ObjectInputFilter.allowFilter(
      cls -> cls.getName().equals("com.mediconnect.dto.ReferralBundle") ?
          ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED,
      ObjectInputFilter.Status.REJECTED));
And REMOVE the readObject method that runs Runtime.exec on the bundle class itself — that's the actual sink.

Use option A. Test by attempting the old base64 RCE payload — should be rejected during JSON parse.

Tag commit: covers #245, #246, #254.
```

---

## Phase 9 — Integrity (signing, hashes, audit)

**Covers:** #8, #15, #44, #50, #107, #174, #177, #184, #188, #208, #227
**Risk:** medium — adds columns and signing flows
**Estimate:** 3–5 hours
**Depends on:** Phase 2 (HMAC keys in env)

### Best practices

- Every uploaded artefact stores a `content_hash` (SHA-256) at upload time. Download endpoint either returns the hash or sets `ETag: "sha256-<hex>"` + `Digest: sha-256=...`.
- PDFs that need legal weight are signed (`itext-sign` or `OpenPDF` signature). Demo can skip a real certificate but must include a PKCS#7 detached signature blob.
- Mutable clinical records carry a `version` + `previous_revision_id` + `edited_by` + `edited_at`. Original rows are never overwritten in place; an `amendments` table holds the history.
- Lab value override stores `original_value` + `amended_by` + `amend_reason`. UI clearly labels "amended".
- Audit log gets an integrity column: `entry_hash = SHA-256(prev_entry_hash || row_json)`. Tamper detection via a verifier job.

### Prompts

**9.1 — Schema migrations**

```
Create migrations:

V25__content_hashes.sql:
ALTER TABLE medical_records ADD COLUMN content_hash CHAR(64) NULL, ADD COLUMN attachment_size BIGINT NULL;
ALTER TABLE lab_results     ADD COLUMN content_hash CHAR(64) NULL;
ALTER TABLE prescriptions   ADD COLUMN pdf_signature TEXT NULL;

V26__lab_amendments.sql:
ALTER TABLE lab_results
    ADD COLUMN amended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN original_value VARCHAR(255) NULL,
    ADD COLUMN amended_by BIGINT NULL,
    ADD COLUMN amended_at DATETIME NULL,
    ADD COLUMN amend_reason VARCHAR(500) NULL,
    ADD CONSTRAINT fk_lab_amender FOREIGN KEY (amended_by) REFERENCES users(id);

V27__clinical_note_revisions.sql:
ALTER TABLE clinical_notes
    ADD COLUMN version INT NOT NULL DEFAULT 1,
    ADD COLUMN previous_revision_id BIGINT NULL,
    ADD COLUMN edited_by BIGINT NULL,
    ADD COLUMN edited_at DATETIME NULL,
    ADD CONSTRAINT fk_note_prev FOREIGN KEY (previous_revision_id) REFERENCES clinical_notes(id);

V28__doctor_license_audit.sql:
ALTER TABLE doctors
    ADD COLUMN license_verified_by BIGINT NULL,
    ADD COLUMN license_verified_at DATETIME NULL,
    ADD CONSTRAINT fk_lic_verifier FOREIGN KEY (license_verified_by) REFERENCES users(id);

V29__audit_log_hash_chain.sql:
ALTER TABLE audit_logs
    ADD COLUMN prev_entry_hash CHAR(64) NULL,
    ADD COLUMN entry_hash CHAR(64) NULL;
CREATE INDEX idx_audit_chain ON audit_logs(entry_hash);

V30__message_redaction_audit.sql:
ALTER TABLE messages
    ADD COLUMN original_content TEXT NULL,
    ADD COLUMN redacted_at DATETIME NULL,
    ADD COLUMN redacted_by BIGINT NULL;

Tag commit (schema only, no code changes): covers groundwork for #44, #50, #174, #177, #184, #188, #208.
```

**9.2 — Compute + persist hashes, sign PDFs**

```
Wire FileStorageService.StoredFile.contentHash into:
- MedicalRecordService.uploadAttachment → record.setContentHash(stored.contentHash())
- LabResultService.saveAttachment → labResult.setContentHash(stored.contentHash())

Add a Digest header on downloads:
  .header("Digest", "sha-256=" + Base64.getEncoder().encodeToString(HexFormat.of().parseHex(record.getContentHash())))

Add Content-MD5 → drop. Use Digest (RFC 3230) with SHA-256.

PDF signing (DoctorPrescribingService.generatePdf, AppointmentController.generatePdf):
- Compute SHA-256 of the rendered PDF bytes.
- Sign with PrescriptionSigner.hmacSign(hashBytes) (HMAC-SHA256 with PRESC_SIGN_KEY).
- Embed the signature in the PDF metadata (OpenPDF supports info dict) OR set a response header:
    .header("Digest", "sha-256="+pdfHash)
    .header("X-Signature-HMAC-SHA256", hex(sig))

Update prescriptions.pdf_signature on first generation; lazy-fill if null.

Tag commit: covers #44, #50, #227.
```

**9.3 — Lab override with amend trail**

```
Open AdminClinicalService.overrideLabResultValue.

New behaviour:
- If labResult.amended is false: copy current resultValue into original_value, mark amended=true, set
  amended_by = current user id, amended_at = now, amend_reason = body.get("amendReason") (required).
- If amended is true and resultValue is being changed again: reject with 409 (single-amend policy) OR
  append to an `lab_result_amendments` history table (preferred — add table in V26 if you want full history).
- Frontend AdminOverridesPage LabsTab: add a required "Reason for amendment" textarea, display an
  "AMENDED" badge in red on the lab row when amended=true, show original_value alongside resultValue.

Tag commit: covers #177.
```

**9.4 — Clinical note revisions instead of in-place overwrite**

```
DoctorNoteService.update:
- Stop mutating the existing row. Instead INSERT a new clinical_notes row with version = existing.version + 1,
  previous_revision_id = existing.id, edited_by = current user id, edited_at = now.
- The "current" revision is the one without any newer row pointing to it via previous_revision_id;
  or add a `is_current` boolean.
- DoctorNoteController.delete: change from hard-delete to soft-delete (set deleted_at).

Frontend DoctorNoteDetailPage: show a revision history strip ("v3 — edited 2026-06-10 by dr.smith — view v2").

Tag commit: covers #107, #208, #209.
```

**9.5 — License verification trail**

```
AdminStaffService.verifyLicense:
- Set licenseVerifiedBy = current user id, licenseVerifiedAt = now.
- Reject any caller that isn't an admin AND require a verification source URL (placeholder for real
  licensing-registry integration). Just store it in a new column `license_verification_source` for now.
- AdminStaffService.updateDoctor: do NOT allow licenseVerified to be patched via this endpoint.
  Force callers through verifyLicense.

Tag commit: covers #179, #180, #184.
```

**9.6 — Audit log hash chain**

```
Create src/main/java/com/mediconnect/security/AuditChainListener.java:

@Component
public class AuditChainListener {
    @PrePersist
    public void prePersist(AuditLog row) {
        AuditLog last = repo.findTopByOrderByIdDesc();
        String prev = last == null ? "GENESIS" : last.getEntryHash();
        row.setPrevEntryHash(prev);
        row.setEntryHash(sha256Hex(prev + "|" + canonicalJson(row)));
    }
}

Add a verifier endpoint GET /api/admin/logs/verify-chain that walks the table and reports the first
broken row.

Tag commit: covers #2 (partial — chain integrity), #65 (compensating control for clear-all).
```

---

## Phase 10 — State machines + A10 (Mishandling)

**Covers:** #43, #59, #60, #92, #108, #110, #128–#144, #211, #223, #233, #254
**Risk:** medium — race fixes need real locking, easy to deadlock
**Estimate:** 3–4 hours

### Best practices

- **State machine** as a `Map<From, Set<To>>` constant. Reject any transition not in the map. Throw a domain exception, not RuntimeException.
- **Pessimistic write lock** on critical mutations: `@Transactional` + `entityManager.lock(entity, LockModeType.PESSIMISTIC_WRITE)`.
- **Database-level UNIQUE** where a logical invariant requires it: `UNIQUE (prescription_id, status)` with a partial index where `status = 'DISPENSED'` — MySQL doesn't support partial indices, so use a separate `dispensed_prescriptions(prescription_id PRIMARY KEY)` table inserted-into in the same transaction.
- **No `catch (Throwable)`** unless you immediately rethrow.
- **No `catch (Exception)` that promotes failure to success.** Failure = FAILED status, never READY.
- **No swallowed `InterruptedException`** — `Thread.currentThread().interrupt()` and break.
- Bounded retry: `MAX_RETRIES = 5` + exponential backoff. Surface `429 Too Many Requests` past the limit.

### Prompts

**10.1 — Appointment + Prescription state machines**

```
Create src/main/java/com/mediconnect/service/AppointmentStateMachine.java:

public final class AppointmentStateMachine {
    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED = Map.of(
        AppointmentStatus.REQUESTED, Set.of(AppointmentStatus.APPROVED, AppointmentStatus.CANCELLED),
        AppointmentStatus.APPROVED,  Set.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED),
        AppointmentStatus.COMPLETED, Set.of(),
        AppointmentStatus.CANCELLED, Set.of()
    );
    public static void requireTransition(AppointmentStatus from, AppointmentStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
            throw new IllegalStateException("Illegal transition: " + from + " → " + to);
    }
}

Same for PrescriptionStateMachine:
- CREATED → DISPENSED, CANCELLED
- DISPENSED → (none)
- CANCELLED → (none)

Call requireTransition(currentStatus, newStatus) before any setStatus(...) in:
- AppointmentService.updateStatus
- PrescriptionService.updateStatus + dispense

Tag commit: covers #43, #59, #60, #110.
```

**10.2 — Refill queue: fix fail-open + race + retry**

```
RefillQueueService:

1. fail-open catch: split into specific catches.
     try { eligibility.check(r); r.setStatus(READY); }
     catch (EligibilityException ee) { r.setStatus(FAILED); r.setFailureReason(ee.getCode()); }
     catch (TimeoutException te)    { r.setStatus(RETRY); r.setRetryAfter(...); }
     // no generic Exception catch.

2. Drop catch (Throwable) around slipPrinter.createSlip:
     try { slipPrinter.createSlip(r); } catch (IOException io) { r.setStatus(FAILED); ... }
     // do not catch Throwable.

3. dispense() lock:
     @Transactional
     public RefillRequest dispense(Long id, Long pharmacistId) {
         RefillRequest r = entityManager.find(RefillRequest.class, id, LockModeType.PESSIMISTIC_WRITE);
         if (r.getStatus() != READY) throw new IllegalStateException("not ready");
         r.setStatus(DISPENSED);
         r.setPharmacistId(pharmacistId);
         return refills.save(r);
     }
   Remove Thread.sleep(50). Remove InterruptedException swallow.

4. @Version column on RefillRequest:
   V31__refill_request_version.sql: ALTER TABLE refill_requests ADD COLUMN version INT NOT NULL DEFAULT 0;
   In RefillRequest.java add @Version private Integer version;

5. retry count column + cap:
   V31 also adds retry_count INT NOT NULL DEFAULT 0.
   RefillController.retry: if (r.getRetryCount() >= 5) return 429; else service.retry(id).
   service.retry: increment retry_count, schedule processOne.

6. EligibilityValidator: explicit null check on quantity:
     if (r.getQuantity() == null) throw new EligibilityException("QUANTITY_NULL");
     if (r.getQuantity() < 1 || r.getQuantity() > 90) throw new EligibilityException("RANGE");

7. V16 retroactive fix: V32__refill_quantity_default.sql:
     UPDATE refill_requests SET quantity = 30 WHERE quantity IS NULL;
     ALTER TABLE refill_requests MODIFY COLUMN quantity INT NOT NULL;

Tag commit: covers #128–#135, #138, #139, #254.
```

**10.3 — Remove failure-reason leak**

```
RefillRequestDto:
- failureReason: only return a code (e.g. "QUANTITY_RANGE", "PHARMACY_DOWN"). Never the raw e.toString().
- tempSlipPath: DELETE from the DTO entirely. The slip-path is server-side only.

LoggingInterceptor: stop persisting raw stack traces in audit_logs.details. Convert to a short error
code via a mapping table; full stack stays in app logs (logback file appender).

Tag commit: covers #136, #137, #144.
```

---

## Phase 11 — Logging hygiene

**Covers:** #66–#70, #123, #142, #148, #159, #160, #167, #176, #184, #188, #198, #201, #202, #203, #222, #232, #242, #253
**Risk:** low — but easy to over-redact and lose diagnostics
**Estimate:** 2–3 hours
**Depends on:** Phase 1

### Best practices

- Redact sensitive request/response bodies at the filter, NOT in audit consumers. Use a regex set:
  `password`, `passwordHash`, `token`, `secret`, `apiKey`, `Authorization`, `pin`, `dob`, `ssn`.
- Replace match value with `***REDACTED***`. Log the redaction count.
- Trust `X-Forwarded-For` only if `req.getRemoteAddr()` is in a configured trusted-proxy list.
- Strip `\r\n` from any header before persistence.
- No stack traces in audit_logs.details — keep stack in app logs (Logback), audit gets a short error code.
- Audit semantic intent: `Auditor.log(actor, action, target, outcome)` not just raw HTTP envelope.
- `DELETE /admin/logs/{id}` removed; `POST /admin/logs/clear` replaced by a retention job (delete rows >90 days).

### Prompts

**11.1 — Redact + sanitise in LoggingInterceptor**

```
Open src/main/java/com/mediconnect/interceptor/LoggingInterceptor.java.

1. Add a redactor:
   private static final Pattern REDACT = Pattern.compile(
       "(\"(password|passwordHash|token|secret|apiKey|pin|authorization)\"\\s*:\\s*)\"[^\"]*\"",
       Pattern.CASE_INSENSITIVE);
   private String redact(String s) { return s == null ? null : REDACT.matcher(s).replaceAll("$1\"***REDACTED***\""); }

2. Apply redact() to: requestBody, responseBody, params string.

3. User-Agent: strip control chars before persist: userAgent.replaceAll("[\\r\\n\\t]", " ").substring(0, Math.min(255, ua.length())).

4. X-Forwarded-For: only honour if remoteAddr is in app.proxy.trusted-cidrs config. Else use remoteAddr.

5. Stop writing stack traces to audit_logs.details. Instead:
   - In your exception handlers (GlobalExceptionHandler), log the stack to the application logger.
   - audit_logs.details receives a short {"errorCode":"XYZ","message":"<one-line>"} JSON.

6. Strip Set-Cookie and Authorization headers from any captured response headers.

Tag commit: covers #66, #67, #68, #69, #70.
```

**11.2 — Decommission destructive audit endpoints**

```
1. Remove POST /api/admin/logs/clear from AdminAuditController. Replace with a @Scheduled job that
   deletes audit_logs rows older than 365 days.
2. Remove DELETE /api/admin/logs/{id} entirely — selective audit tampering has no legitimate use.
3. AdminPage.tsx + AdminLogsPage.tsx: remove "Clear All Logs" and per-row Delete buttons. Keep Export.

Tag commit: covers #65, #160, #123.
```

**11.3 — Soft-delete users, soft-archive medical records**

```
1. AdminUserService.deleteUser: change from deleteById to user.setActive(false). Add deleted_at column
   via V33__user_soft_delete.sql.
2. AdminUserService.bulkDelete: cap at 50 ids per request; for each, soft-delete.
3. AdminClinicalService.deleteMedicalRecord: soft-delete (add deleted_at on medical_records via V33).
4. V17/V18 cascade FKs: keep, but they now only cascade when the parent is fully hard-deleted (rare).
5. AdminBroadcastService.redact: copy current content to original_content (column added in V30),
   set redacted_by + redacted_at; do not allow re-redaction.

Tag commit: covers #148, #152, #176, #188.
```

**11.4 — Auditor service for semantic events**

```
Create src/main/java/com/mediconnect/security/Auditor.java:

@Service
public class Auditor {
    public void log(String action, String entityType, Long entityId, String outcome, Map<String,Object> extras) {
        AuditLog row = AuditLog.builder()
            .userId(currentUserId()).action(action).entityType(entityType).entityId(entityId)
            .details(objectMapper.writeValueAsString(Map.of("outcome", outcome, "extras", extras)))
            .createdAt(LocalDateTime.now())
            .build();
        repo.save(row);  // AuditChainListener stamps prev/entry hash
    }
}

Inject + call from every privileged service method:
- AdminUserService.impersonate → auditor.log("IMPERSONATE_START", "user", id, "ok", Map.of("reason", reason))
- DoctorNoteService.delete → auditor.log("NOTE_DELETE", "clinical_note", id, "ok", ...)
- DoctorPrescribingService.sign → auditor.log("PRESCRIPTION_SIGN", "prescription", id, "ok", ...)
- AdminClinicalService.overrideLabResultValue → auditor.log("LAB_AMEND", ...)

Tag commit: covers #198, #201, #209, #222, #232, #242, #253.
```

---

## Phase 12 — Frontend hardening sweep

**Covers:** the remaining frontend vulns not closed by 3.5, 6.2, 7.2
**Risk:** medium — UX-visible
**Estimate:** 2–3 hours
**Depends on:** 2, 3, 4, 7

### Best practices

- No client-side decoded JWT for authorization decisions. Source of truth = server via `/api/auth/me`.
- No localStorage for tokens or hashes.
- Remove "demo cheat sheet" placeholder text (SQLi/XSS suggestions in inputs).
- Strict CSP: `default-src 'self'; script-src 'self'; frame-ancestors 'none'; object-src 'none'`.
- Disable autocomplete on sensitive fields; `password` inputs always `type="password"`.
- Confirm dialogs on destructive ops.

### Prompts

**12.1 — Strip all hint placeholders**

```
In mediconnect-frontend, remove the "try the attack" hint text and dangerous defaults:

- LabOrderForm.tsx placeholder: replace "file:///etc/hosts  or  http://169.254.169.254/..." with
  "https://drugs.example.com/catalogue/<id>".
- DoctorPatientsPage.tsx search placeholder: replace "name or email — try ' OR '1'='1" with "Search by name or email".
- MessagesPage.tsx compose: remove the "<img src=x onerror=alert(1)>" hint paragraph.
- NoteEditor.tsx: confirm the templateBody default is empty after Phase 8.2 changes.
- DrugInteractionPanel.tsx: remove the "Java.type(...)" demo payload text.
- LabResultsPage.tsx export toast: drop the [A06] tag from toast messages — Phase 12.3 handles UI labels.

Tag commit: closes the cheat-sheet half of #84, #194, #224, #235.
```

**12.2 — Lock down Admin create-user UI**

```
AdminPage.tsx + AdminUsersPage.tsx:

1. Create-user modal: role dropdown limited to PATIENT, LAB_TECH, PHARMACIST. To create DOCTOR or ADMIN,
   require a separate "Promote" action under user detail view (which calls PUT /users/{id}/role with
   @PreAuthorize hasRole('ADMIN') in backend already locked by Phase 4).
2. Password field: type="password", with a "Reveal" toggle button.
3. Add confirm() dialog on toggle-active + delete + impersonate.
4. Bulk-delete: max 50 selection enforced client-side; backend caps too (Phase 11.3).

Tag commit: covers #119, #120, #121, #122, #123, #157.
```

**12.3 — Remove demo-cheat UI labels (optional, deferred per user request)**

```
SKIP this prompt for now. The "[A0X]" inline labels stay until a later UX pass.
```

---

## Phase 13 — Retire genuinely dangerous endpoints

**Covers:** #168, #169, #170, #65, #160, #151
**Risk:** low — already wrapped by Phase 4 authz, but cleaner to remove
**Estimate:** 1 hour
**Depends on:** Phase 4

### Prompts

**13.1 — Delete or restrict admin/maintenance/***

```
AdminOpsController + AdminOpsService:

- /maintenance/run-sql: DELETE the endpoint + the service method. Update AdminOpsPage.tsx
  RunSqlConsole component: replace the UI with a static "Removed for safety. Use audit log export
  for read-only queries via support ticket."
- /maintenance/restart: DELETE the endpoint. Restarts go through ops, not HTTP.
- /maintenance/backup: rewrite to invoke a fixed mysqldump with no shell, no query string. Spawn via
  ProcessBuilder with explicit args list (NOT "/bin/sh -c"):
    new ProcessBuilder("/usr/bin/mysqldump", "--no-create-db", "--user=" + cfg.dbUser, ...,
                       cfg.dbName).redirectErrorStream(true).start();
  Pin the dbName from configuration; never read from the request.

Tag commit: covers #168, #169, #170.
```

**13.2 — Tighten impersonation**

```
AdminUserService.impersonate:

- Require header X-Impersonation-Reason with length ≥ 10. Reject otherwise.
- Mint the JWT with an additional claim `impersonated_by = currentAdminId`.
- Auditor.log("IMPERSONATE_START", ...) BEFORE returning the token.
- Add a server-side @Scheduled job that auto-revokes impersonation tokens older than 30 minutes:
  store impersonation_sessions(id, admin_id, target_user_id, started_at, ended_at) and the
  JwtAuthenticationFilter consults it on requests.

Frontend ImpersonateBanner: read impersonation state from /api/auth/me (server-side) — not from
localStorage. Hiding the banner requires actually ending the session via POST /api/auth/impersonation/end.

Tag commit: covers #151, #156.
```

---

## Phase 14 — Final sweep + smoke

**Covers:** the long tail (anything still ⬜ in VULN_FIX_MAP.md)
**Risk:** varies
**Estimate:** 1–3 hours

### Prompts

**14.1 — Find what's left**

```
1. Open VULN_FIX_MAP.md and list every row still ⬜.
2. For each, locate the file in the codebase, decide:
   - Already fixed transitively? Mark ✅ with the commit SHA of the phase that closed it.
   - Trivial leftover? Fix it in this final commit.
   - Truly out of scope? Mark 🚫 wont-fix with a one-line note.

Common stragglers expected after Phases 1–13:
- #99 (user ID in greeting banner) — UX choice, keep or remove.
- #114 (clipboard export of lab results) — pair with backend permissions check on the underlying query.
- #117 (replySenderId in MessagesPage) — already handled by Phase 4 service rejecting body senderId,
  but the UI select still exists. Remove the <select>.
- #185 redact column on the messages table — already handled by Phase 11.3.
- #243, #244 (telemedicine iframe Referer + Content-Disposition) — add Referrer-Policy: no-referrer on
  the /doctor/telemedicine/* route + Content-Disposition: attachment on the iCal feed.

Tag commit: covers final cleanup batch.
```

**14.2 — Final smoke + tests**

```
1. ./mvnw test — should pass.
2. Frontend: npx tsc -b --noEmit + npx eslint . — green.
3. Run the §0.2 smoke endpoint list manually. Every call returns 2xx for the right caller and 401/403
   for the wrong caller.
4. Login as patient1 → dashboard, messages, lab results, prescriptions all render.
5. Login as doctor1 → roster, chart, note create, lab order create.
6. Login as admin → users, audit logs export, system health.
7. Verify `git diff vulnerable..fixes -- VULNERABLE_CONFIG.md` is minimal — the vuln doc itself shouldn't
   need editing on the fix branch (it documents the vulnerable starting state).
8. Compare VULN_FIX_MAP.md — every row ✅ or 🚫. No ⬜ remaining.

Tag final commit: `git tag fix-complete-v1`.
```

---

## Phase 15 — Appointments tab (Module G) hardening

**Covers:** #256–#268 (added in Appointments-tab redesign, see `APPOINTMENTS_PLAN.md`).
**Risk:** medium — touches the same `appointments` table the patient dashboard reads. Keep `/api/appointments` byte-identical.
**Estimate:** 4–6 hours
**Depends on:** Phase 1, Phase 3 (auth), Phase 4 (authz), Phase 5 (SQLi), Phase 7 (XSS), Phase 11 (logging)

### Best practices

- **Slot reservation**: before `setStatus(APPROVED)` on any appointment, query for overlaps in `(doctor_id, scheduledAt ± slotDurationMin)`. Reject the second-writer with a `409` and a `conflictingAppointmentId` field. (#256)
- **Identity**: drop `actorDoctorId` from the request body. Pull the acting doctor from `SecurityContextHolder` and verify the user has `DOCTOR` role on the JWT. (#257)
- **HTML escape on display**: decline_reason and notes are clinician input — escape on render (the React text-renderer does this by default; remove every `dangerouslySetInnerHTML` from `DoctorAppointmentsPage.tsx`). (#258)
- **Revision table**: introduce `appointment_revisions(id, appointment_id, prev_requested_date, new_requested_date, prev_notes, new_notes, changed_at, changed_by)`. Reschedule writes a row instead of overwriting `original_date`. (#259)
- **Two-step complete**: split `/complete` into `mark-completed` (status only) and `attach-record` (separate POST to `MedicalRecordController` with its own role check + content_hash + signing). The chart row creation should never be a side-effect of an appointment update. (#260)
- **Rate limits**: bucket per-doctor `/no-show` and `/bulk-status` at 30 / minute. Use Bucket4j or a Redis token-bucket. (#261, #262)
- **Bulk caps**: `/bulk-status` rejects `ids.size() > 100`. `/import` rejects file > 1 MB and `>= 1000` rows. (#262, #263)
- **CSV formula sanitisation**: on export, prefix any cell value beginning with `=`, `+`, `-`, `@`, tab, or CR with a single quote. (#264, #265)
- **CSV parser**: replace `String.split(",", -1)` with a CSV library that handles quoting + escaping (e.g., Apache Commons CSV).
- **Content-Disposition**: serve `export.csv` with `Content-Disposition: attachment; filename=appointments.csv`. (#266)
- **SQL injection**: convert the raw `createNativeQuery` builder to a Criteria API query or a parameterised `@Query` with `:q`, `:status`, `:from`, `:to` placeholders. (#267)
- **Conflicts endpoint**: require `?doctorId` and verify it matches the JWT principal's doctor row. (#268)

### Prompts

**15.1 — Slot reservation + identity hardening**

```
Open src/main/java/com/mediconnect/service/DoctorAppointmentService.java.

1. In #approve(Long id, Map<String,Object> body):
   - Drop the body argument entirely. Replace the signature with:
       approve(Long id, Authentication auth)
   - Resolve acting doctor:
       User caller = userRepository.findByUsername(auth.getName()).orElseThrow();
       Doctor actor = doctorRepository.findByUserId(caller.getId()).orElseThrow();
       if (!caller.getRole().equals(Role.DOCTOR)) throw new ResponseStatusException(FORBIDDEN);
   - Query for overlaps:
       List<Appointment> overlaps = appointmentRepository.findOverlapping(
           a.getDoctor().getId(), a.getRequestedDate().minusMinutes(15),
           a.getRequestedDate().plusMinutes(15), List.of(APPROVED, COMPLETED));
       if (!overlaps.isEmpty()) throw new ResponseStatusException(CONFLICT,
           "Slot occupied by appointment #" + overlaps.get(0).getId());
   - Set actor_doctor_id to actor.getId(), NOT from the body.

2. Add findOverlapping to AppointmentRepository:
   @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :d
            AND a.status IN :statuses
            AND a.requestedDate BETWEEN :from AND :to")
   List<Appointment> findOverlapping(@Param("d") Long doctorId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     @Param("statuses") List<AppointmentStatus> statuses);

3. Same shape change for #decline (no actor from body), #reschedule, #complete.

4. DoctorAppointmentController: change every Module G endpoint to take `Authentication auth` and forward to service.

Smoke: POST /approve same doctor, overlapping slot → 409 with conflictingAppointmentId. Closes #256, #257.
```

**15.2 — Revision table for reschedule**

```
Create V26__appointment_revisions.sql:
  CREATE TABLE appointment_revisions (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      appointment_id BIGINT NOT NULL,
      prev_requested_date DATETIME,
      new_requested_date DATETIME,
      prev_notes TEXT,
      new_notes TEXT,
      reason VARCHAR(500),
      changed_by_user_id BIGINT,
      changed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE
  );

In DoctorAppointmentService#reschedule:
  Save a new AppointmentRevision row BEFORE mutating the Appointment.

Drop the rescheduled_at / original_date columns on `appointments` (V27 ALTER) — they encouraged the single-prior-value pattern that lost history.

Smoke: reschedule twice; both prior values still queryable via /appointments/{id}/revisions. Closes #259.
```

**15.3 — Remove auto-MedicalRecord side effect**

```
In DoctorAppointmentService#complete:
  - Stop calling medicalRecordRepository.save(rec).
  - Return the updated AppointmentRowDto only.

Frontend CompleteAppointmentDialog: after the complete call succeeds, POST a separate /api/medical-records request (use the existing MedicalRecordController, NOT the new auto path) with the notes as `diagnosis`. The chart row creation now goes through the same role/content-hash/audit path as any other clinical row.

Smoke: complete one appointment → exactly one appointment update + one separate medical_records row. Closes #260.
```

**15.4 — Rate limits + bulk caps**

```
Add bucket4j-spring-boot-starter to pom.xml. Wire two buckets:
  - per-doctor /no-show: 30 calls/min
  - per-caller /bulk-status: 10 calls/min
  - per-caller /import: 5 calls/hour

In bulkStatus(): if ids.size() > 100, throw 413.
In importCsv(): if file.getSize() > 1_000_000, throw 413. If row counter passes 1000, throw 413 mid-stream and rollback the transaction.

Smoke: bulk of 200 ids → 413. Closes #261, #262, #263.
```

**15.5 — CSV sanitisation + Content-Disposition**

```
In DoctorAppointmentService#exportCsv:
  private String csvCell(String s) {
      if (s == null) return "";
      String first = s.isEmpty() ? "" : String.valueOf(s.charAt(0));
      String escaped = (first.equals("=") || first.equals("+") || first.equals("-")
                       || first.equals("@") || first.equals("\t") || first.equals("\r"))
          ? "'" + s : s;
      // RFC 4180 quote
      if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
          escaped = "\"" + escaped.replace("\"", "\"\"") + "\"";
      }
      return escaped;
  }
  Apply to every cell.

In DoctorAppointmentController#exportCsv:
  h.setContentDispositionFormData("attachment", "appointments.csv");

In importCsv(): replace String.split with org.apache.commons.csv.CSVFormat.RFC4180 parser. Reject rows whose `notes` column starts with =, +, -, @ (return them in the response body as `rejected: [...]`).

Smoke: export response includes Content-Disposition: attachment + every formula prefix `'`-escaped. Closes #264, #265, #266.
```

**15.6 — SQL parameterisation + conflicts auth**

```
In DoctorAppointmentService#list:
  Replace createNativeQuery+StringBuilder with a Criteria query OR
  @Query with named parameters:
  @Query("SELECT a FROM Appointment a WHERE
            (:doctorId IS NULL OR a.doctor.id = :doctorId)
            AND (:status IS NULL OR a.status = :status)
            AND (:from IS NULL OR a.requestedDate >= :from)
            AND (:to   IS NULL OR a.requestedDate <= :to)
            AND (:q    IS NULL OR LOWER(a.notes) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY a.requestedDate ASC")

In DoctorAppointmentService#conflicts:
  Require non-null doctorId. Verify it matches caller's principal (same pattern as 12.1).
  Throw 400 if doctorId missing, 403 if mismatched.

Smoke: ?q=' OR '1'='1 returns 0 rows (no injection). Closes #267, #268.
```

### Sign-off

Tag commit: closes #256–#268. VULN_FIX_MAP.md rows for 256–268 set to ✅.

---

## Phase 16 — Doctor create-appointment (#269)

**Covers:** #269
**Risk:** low — same endpoint as patient create; fix is server-side only
**Depends on:** Phase 4 (ownership enforcement pattern)

**16.1 — Derive doctorId from JWT principal**

```
In AppointmentController.POST /api/appointments (or AppointmentService.create):

  // Resolve the authenticated doctor's profile from the JWT subject instead of trusting the request body.
  String email = SecurityContextHolder.getContext().getAuthentication().getName();
  Doctor doctor = doctorRepository.findByUserEmail(email)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a doctor"));
  appointment.setDoctorId(doctor.getId());  // ignore body doctorId

Tag commit: closes #269.
```

---

## Appendix A — Sample fix-commit message format

```
[A0X] fix(<area>): <short summary>

Closes vulns #N, #M, #P from VULN_FIX_MAP.md.

- <bullet of what changed>
- <bullet of what changed>

Functional smoke: verified <endpoint> still returns <expected>.
```

## Appendix B — Things NOT to change

- The OWASP-comment tags `[A0X]` in source-code comments. Future code review uses them to find the
  before/after diff. Leave them in the comments even after the code is fixed.
- VULNERABLE_CONFIG.md content — it describes the vulnerable branch state. Don't rewrite it on fixes.
  Update VULN_FIX_MAP.md instead.
- Test seed data hashes — V10/V11/V12 still seed users with MD5 hashes; the Phase 2.1 verifyPassword
  shim lets old hashes log in and re-hashes on success. Don't preemptively rewrite the seed.

## Appendix C — Rollback plan per phase

If a phase breaks smoke:

```bash
git log --oneline <branch>          # find the offending commit
git revert <sha>                    # reverse it; commit history stays clean
./mvnw test && npx tsc -b           # confirm green
# investigate, fix forward, never delete history
```

Worst case full rollback:

```bash
git checkout fixes
git reset --hard pre-fix-baseline   # WIPES all work on fixes branch
```

---

## Note — Patient Modal ID Scrub — 2026-06-19

UI-only change. No fix-branch work needed. The following raw IDs were hidden for PATIENT role in detail modals:

- `PrescriptionsPage.tsx`: `doctorId` fallback → `'—'`; `medicalRecordId` and `pharmacistId` blocks guarded with `!isPatient`
- `LabResultsPage.tsx`: `patientId` sub-line and `labTechId` block guarded with `!isPatient`

Backend still returns all IDs in API responses — no tracker rows affected.

## Note — Password Reset Dialog — 2026-06-19

Admin reset-password UX upgraded. Changes:

- `AdminUserController.java` / `AdminUserService.java`: `POST /admin/users/{id}/reset-password` now accepts optional `{ "newPassword": "..." }` body — uses supplied value if non-blank, otherwise generates random. Enhances the [A07] surface of vuln #150 (direct password injection).
- `admin.ts`: `adminApi.resetPassword(id, newPassword?)` — passes optional body.
- `AdminUserDetailPage.tsx`: one-click button replaced with a dialog containing a text input for the desired password plus a client-side "Generate" helper. On success the result dialog opens as before showing the plaintext for copy.

Fix-branch notes for #150: also remove the `newPassword` passthrough — force server-side generation (or enforce complexity) and stop returning plaintext in response body.

Only use the hard reset if the fix branch is unsalvageable.
