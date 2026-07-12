# Vulnerability Fix Map

Companion to VULNERABLE_CONFIG.md. Tracks fix-branch progress on each of the 268 demo vulnerabilities.

> #236–#244 (Module E Telemedicine) marked 🚫 wont-fix — Telemedicine tab deleted from the Doctor Console. Backend `DoctorSessionController`, `DoctorSessionService`, `TelemedicineSession` entity + repo + DTO, `TelemedicineRoom` component, `DoctorSessionsPage`, `DoctorSessionRoomPage` all removed. `V23__telemedicine_sessions.sql` migration left on disk for Flyway history continuity; table is orphan.
>
> #247–#251 + #255 were marked 🚫 wont-fix when the AI Assist surface was removed in the Appointments-tab redesign (`APPOINTMENTS_PLAN.md`). The Referral half of Module F (#245, #246, #252, #253, #254) survives.
>
> **UX-scoping note (#257, #268):** the Appointments page now derives the active doctor from `/api/doctors/profile` (logged-in session) so the UI no longer asks for a Doctor ID. The underlying [A07] / [A01] weaknesses on the backend remain — actorDoctorId is still trusted from the request body when overridden, and the conflicts endpoint still returns every doctor when called without a filter. Phase 15 prompts unchanged.
>
> **Patient view UI scrub — 2026-06-19 (#43, #92):** the "Update status" dropdown + button in the `AppointmentsPage.tsx` detail modal is now wrapped with `{!isPatient && (...)}`. PATIENT users no longer see the control in the UI. Rows #43 and #92 remain ⬜ — the backend (`PUT /api/appointments/{id}/status`, `PUT /api/appointments/{id}`) still accepts any status from any token with no ownership check and no state-machine enforcement. Demo with a patient JWT: `curl -X PUT .../api/appointments/{id}/status -d '{"status":"APPROVED"}'` → 200. Phase 10 fix prompts unchanged.
>
> **Patient profile UI scrub — 2026-06-19:** the "Account Activity" card (`failedLoginAttempts`, `lockedUntil`, `createdAt`) was removed from the patient-facing `ProfilePage.tsx`. This card had no dedicated vulnerability number; the API-level data leak (`GET /api/users/{id}` still returns all three fields in every response) is covered by the `UserDto` field exposure and IDOR entries. The same fields remain visible in `AdminUserDetailPage.tsx`. No tracker row changes.
>
> **Patient modal ID scrub — 2026-06-19:** raw numeric IDs (`doctorId`, `medicalRecordId`, `pharmacistId`, `patientId`, `labTechId`) hidden behind `!isPatient` guards in `PrescriptionsPage.tsx` and `LabResultsPage.tsx` detail modals. Backend API still returns all fields — UI-only change. No new vulnerability numbers; no tracker row changes.
>
> **Password reset dialog — 2026-06-19:** admin reset-password flow upgraded from a one-click button to a dialog with a password input and "Generate random" helper. Backend `POST /admin/users/{id}/reset-password` now accepts optional `{ newPassword }` body — caller can set any exact password ([A07], enhances #150). Frontend: `AdminUserDetailPage.tsx`, `admin.ts#resetPassword`. Vuln #150 description updated.

## How to use

- One row per vulnerability number (#1–#268).
- **Status** legend: ⬜ pending · 🔄 in-progress · ✅ fixed · 🚫 wont-fix (e.g. dead code already removed).
- Drop the fix commit SHA in the **Fix Commit** column once the fix lands on the fix branch.
- See VULNERABLE_CONFIG.md for the full description of each row.

## Tracker

| # | OWASP | Title | Fix Commit | Status |
|---|---|---|---|---|
| 1 | A04 | Hardcoded database credentials |  | ⬜ |
| 2 | A04 | SQL schema leakage via logs |  | ⬜ |
| 3 | A04 | Destructive schema changes |  | ⬜ |
| 4 | A04 | Actuator endpoints publicly exposed |  | ⬜ |
| 5 | A04 | Stack trace exposed to client |  | ⬜ |
| 6 | A04 | Thymeleaf cache disabled |  | ⬜ |
| 7 | A06 | PII stored as plaintext without encryption — V2 |  | ⬜ |
| 8 | A08 | No content_hash for medical records — V5 |  | ⬜ |
| 9 | A05 | Stored XSS — messages.content without sanitization — V8 |  | ⬜ |
| 10 | A04 | MD5 seed passwords without salt — V10 |  | ⬜ |
| 11 | A06 | passwordHash exposed in API response — `User.java`, `UserDto.java` |  | ⬜ |
| 12 | A06 | PII fields exposed in API response without masking — `Patient.java`, `PatientDto.java` |  | ⬜ |
| 13 | A07 | Mass Assignment — same DTO for request and response — all `*Dto.java` |  | ⬜ |
| 14 | A05 | XSS — `Message.content` without sanitization — `Message.java`, `MessageDto.java` |  | ⬜ |
| 15 | A08 | File integrity unverifiable — `MedicalRecord.java`, `MedicalRecordDto.java` |  | ⬜ |
| 16 | A04/A06 | Hardcoded JWT secret in source code — `JwtUtil.java` |  | ⬜ |
| 17 | A04 | JWT key below 256 bits — WeakKeyException bypassed — `JwtUtil.java` |  | ⬜ |
| 18 | A07 | JWT token valid for 30 days — `JwtUtil.java` |  | ⬜ |
| 19 | A07 | Algorithm Confusion attack — `JwtUtil.java` |  | ⬜ |
| 20 | A06 | MD5 without salt — rainbow table attack — `PasswordUtils.java` |  | ⬜ |
| 21 | A06 | Timing attack on password comparison — `PasswordUtils.java` |  | ⬜ |
| 22 | A01/A06 | All endpoints unauthenticated + CSRF disabled — `SecurityConfig.java` |  | ⬜ |
| 23 | A07 | Mass Assignment — role taken directly from request body |  | ⬜ |
| 24 | A07 | No password complexity validation |  | ⬜ |
| 25 | A07 | User Enumeration — distinct error messages on both register and login |  | ⬜ |
| 26 | A06 | JWT in response body — not in HttpOnly cookie |  | ⬜ |
| 27 | A07 | No rate limiting on /login |  | ⬜ |
| 28 | A01 | CSRF fully disabled — `SecurityConfig.java` |  | ⬜ |
| 29 | A01 | Admin routes open to all — no role check — `SecurityConfig.java` |  | ⬜ |
| 30 | A05 | Wildcard CORS policy — `SecurityConfig.java` |  | ⬜ |
| 31 | A04 | All security response headers disabled — `SecurityConfig.java` |  | ⬜ |
| 32 | A04 | `NoOpPasswordEncoder` registered — plain-text comparison path — `SecurityConfig.java` |  | ⬜ |
| 33 | A07 | Token expiry skipped for specific paths — `JwtAuthenticationFilter.java` |  | ⬜ |
| 34 | A07 | Token parsing exceptions silently swallowed — `JwtAuthenticationFilter.java` |  | ⬜ |
| 35 | A07 | User Enumeration in `UserDetailsService` — `CustomUserDetailsService.java` |  | ⬜ |
| 36 | A01 | IDOR — GET /api/users/{id} without authorization check — `UserController.java` |  | ⬜ |
| 37 | A06 | passwordHash exposed in API response — `UserService.java`, `UserDto.java` |  | ⬜ |
| 38 | A01 | Mass Assignment — PUT /api/users/{id}/role accepts role without server-side validation — `UserController.java` |  | ⬜ |
| 39 | A07 | DELETE operation implemented as @GetMapping — `UserController.java` |  | ⬜ |
| 40 | A01 | GET /api/users returns all users to all roles — `UserController.java` |  | ⬜ |
| 41 | A05 | SQL Injection — GET /api/appointments?doctorName= — `AppointmentService.java` |  | ⬜ |
| 42 | A01 | IDOR — GET /api/appointments/{id} without ownership check — `AppointmentController.java` |  | ⬜ |
| 43 | A02 | Missing state machine — PUT /api/appointments/{id}/status — `AppointmentService.java` |  | ⬜ |
| 44 | A08 | PDF served without Content-MD5 or checksum — `AppointmentController.java` |  | ⬜ |
| 45 | A01 | Broken Access Control — POST /api/medical-records without doctor-patient assignment check — `MedicalRecordService.java` |  | ⬜ |
| 46 | A05 | Unrestricted File Upload — POST /api/medical-records/{id}/attachment — `MedicalRecordService.java` |  | ⬜ |
| 47 | A05 | Path Traversal (write) — getOriginalFilename() without sanitization — `MedicalRecordService.java` |  | ⬜ |
| 48 | A05 | Path Traversal (read) — filePath query parameter without canonical validation — `MedicalRecordController.java`, `MedicalRecordService.java` |  | ⬜ |
| 49 | A04 | Filesystem path returned in response — `MedicalRecordController.java` |  | ⬜ |
| 50 | A08 | content_hash not computed at upload time — `MedicalRecordService.java` |  | ⬜ |
| 51 | A05 | SQL Injection — GET /api/lab-results/search with 4 parameters — `LabResultService.java` |  | ⬜ |
| 52 | A01 | IDOR — GET /api/lab-results/{id} without ownership check — `LabResultController.java` |  | ⬜ |
| 53 | A05 | Unrestricted File Upload + predictable filename — `LabResultService.java` |  | ⬜ |
| 54 | A05 | Path Traversal (read) — GET /api/lab-results/{id}/file?filePath= — `LabResultService.java` |  | ⬜ |
| 55 | A05 | Stored XSS — POST /api/messages stores content without sanitization — `MessageService.java` |  | ⬜ |
| 56 | A07 | Sender spoofing — senderId taken from request body — `MessageService.java` |  | ⬜ |
| 57 | A01 | IDOR — GET /api/messages/conversation/{userId} without participant check — `MessageController.java` |  | ⬜ |
| 58 | A01 | Broken Access Control — DELETE /api/messages/{id} without ownership check — `MessageService.java` |  | ⬜ |
| 59 | A02 | Missing state machine — PUT /api/prescriptions/{id}/dispense allows double dispensing — `PrescriptionService.java` |  | ⬜ |
| 60 | A02 | Missing state machine — PUT /api/prescriptions/{id}/status allows any transition — `PrescriptionService.java` |  | ⬜ |
| 61 | A07 | Pharmacist identity not verified — PUT /{id}/dispense — `PrescriptionController.java` |  | ⬜ |
| 62 | A01 | Broken Access Control — GET /api/admin/users accessible without ADMIN role — `AdminController.java` |  | ⬜ |
| 63 | A07 | Mass Assignment — POST /api/admin/users creates accounts with any role — `AdminService.java` |  | ⬜ |
| 64 | A04 | Sensitive Data Exposure — GET /api/admin/config returns raw Environment — `AdminService.java` |  | ⬜ |
| 65 | A09 | Security Logging Failure — POST /api/admin/logs/clear destroys audit trail — `AdminService.java` |  | ⬜ |
| 66 | - | Information Exposure Through Error Message — ex.printStackTrace(pw) [A08 / CWE-209] — `LoggingInterceptor.java` |  | ⬜ |
| 67 | - | Sensitive parameters logged without masking [A04 / A09] — `LoggingInterceptor.java` |  | ⬜ |
| 68 | - | Log Injection via unsanitized User-Agent [A05 / CWE-117] — `LoggingInterceptor.java` |  | ⬜ |
| 69 | A06 | Spoofable IP address via X-Forwarded-For — `LoggingInterceptor.java` |  | ⬜ |
| 70 | A06 | Response body logged verbatim — JWT and passwordHash duplicated in audit table — `LoggingInterceptor.java` |  | ⬜ |
| 71 | A04 | Internal error messages forwarded to client — `GlobalExceptionHandler.java` |  | ⬜ |
| 72 | A04 | DB table name and PK exposed on 404 — `GlobalExceptionHandler.java`, `EntityNotFoundException.java` |  | ⬜ |
| 73 | A04 | JVM-level error forwarded to client — `GlobalExceptionHandler.java` |  | ⬜ |
| 74 | - | Unbounded memory buffering — ContentCachingRequestWrapper without size limit [A08 / CWE-400] — `ContentCachingFilter.java` |  | ⬜ |
| 75 | - | Response body buffered without limit [A08 / CWE-400] — `ContentCachingFilter.java` |  | ⬜ |
| 76 | - | JWT stored in localStorage [A04 / CWE-922] — `AuthContext.tsx` |  | ⬜ |
| 77 | - | Client-side JWT decode without signature verification [A04 / CWE-345] — `AuthContext.tsx` |  | ⬜ |
| 78 | - | Client-side role-based access control [A01 / CWE-602] — `ProtectedRoute.tsx` |  | ⬜ |
| 79 | - | Full user object (including passwordHash) persisted in localStorage [A02 / CWE-312] — `AuthContext.tsx` |  | ⬜ |
| 80 | A04 | Raw server error message rendered directly in UI — `LoginPage.tsx` |  | ⬜ |
| 81 | - | Token read from localStorage injected into every HTTP request [A04 / A07] — `axiosInstance.ts` |  | ⬜ |
| 82 | A04 | passwordHash column displayed in admin table — `DashboardPage.tsx` |  | ⬜ |
| 83 | A06 | localStorage JWT visible in Token Inspector widget — `DashboardPage.tsx` |  | ⬜ |
| 84 | - | dangerouslySetInnerHTML — Stored XSS in message rendering [A05 / CWE-79] — `MessagesPage.tsx` |  | ⬜ |
| 85 | - | JWT token embedded as URL query parameter [A04 / CWE-598] — `ProfilePage.tsx` |  | ⬜ |
| 86 | - | Admin page accessible to all authenticated users — no role guard [A01 / CWE-285] — `App.tsx` + `AdminPage.tsx` |  | ⬜ |
| 87 | - | Role change via Mass Assignment in Admin UI [A07 / CWE-915] — `AdminPage.tsx` |  | ⬜ |
| 88 | - | Sender ID spoofing in message compose form [A07 / CWE-284] — `MessagesPage.tsx` |  | ⬜ |
| 89 | A01 | IDOR — GET /api/patients/by-user/{userId} exposes full PII without ownership check — `PatientController.java` |  | ⬜ |
| 90 | A01 | Broken Access Control — GET /api/stats/summary with no role check — `StatsController.java` |  | ⬜ |
| 91 | A01 | Broken Access Control — GET /api/stats/charts with no role check — `StatsController.java` |  | ⬜ |
| 92 | A01 | IDOR — PUT /api/appointments/{id} without ownership check — `AppointmentController.java` |  | ⬜ |
| 93 | A01 | Broken Access Control — GET /api/medical-records returns all records to all roles — `MedicalRecordController.java` |  | ⬜ |
| 94 | A01 | IDOR — GET /api/messages/conversations userId parameter not verified against JWT — `MessageController.java` |  | ⬜ |
| 95 | A01 | IDOR — PUT /api/users/{id} updates any user's profile without ownership check — `UserController.java` |  | ⬜ |
| 96 | A04 | MD5 seed passwords without salt — V11 and V12 |  | ⬜ |
| 97 | A05 | Broken Access Control — GET /api/stats/recent returns all users' activity with no auth filter — `StatsController.java`, `StatsService.java` |  | ⬜ |
| 98 | A05 | Extended summary exposes system-wide unread message count — `StatsService.getSummary()` |  | ⬜ |
| 99 | A06 | Role-aware greeting banner exposes user ID in plaintext — `DashboardPage.tsx` |  | ⬜ |
| 100 | A05 | Recent activity feed renders all-users events client-side — `DashboardPage.tsx` |  | ⬜ |
| 101 | A01 | Sidebar unread badge polls all conversations via userId query param — `Sidebar.tsx` |  | ⬜ |
| 102 | A01 | `/staff` route has no role guard — any authenticated user can access it — `App.tsx` |  | ⬜ |
| 103 | A01 | Sidebar Dashboard link is role-aware client-side only — no server enforcement — `Sidebar.tsx` |  | ⬜ |
| 104 | A01 | Patient dashboard fetches all appointments — client-side filter only — `DashboardPage.tsx` |  | ⬜ |
| 105 | A01 | Doctor dashboard fetches all appointments — client-side filter — `StaffDashboardPage.tsx` |  | ⬜ |
| 106 | A01 | `GET /lab-results/search` with no patientId returns all patients' results — `LabResultService.java` |  | ⬜ |
| 107 | A01 | `PUT /api/medical-records/{id}` — no ownership check — `MedicalRecordController.java`, `MedicalRecordService.java` |  | ⬜ |
| 108 | A01 | `PUT /api/prescriptions/{id}/dispense` — no pharmacist role check — `PrescriptionController.java` |  | ⬜ |
| 109 | A07 | `pharmacistId` from request body — mass assignment — `PrescriptionController.java` |  | ⬜ |
| 110 | A02 | No state machine on dispense — double dispensing and void prescription dispensing — `PrescriptionService.java` |  | ⬜ |
| 111 | A01 | Edit button in Medical Records — no ownership check on server — `MedicalRecordsPage.tsx` |  | ⬜ |
| 112 | A01 | Dispense button shown for all roles — `MedicalRecordsPage.tsx` Prescriptions tab |  | ⬜ |
| 113 | A01 | `LabResultController` — `defaultValue = "0"` masked null, causing staff to see zero results — `LabResultController.java` |  | ⬜ |
| 114 | A06 | Export to clipboard in Lab Results — patient data without access check — `LabResultsPage.tsx` |  | ⬜ |
| 115 | A01 | `PATCH /api/messages/{id}/read` — no ownership check — `MessagesPage.tsx` |  | ⬜ |
| 116 | A01 | `DELETE /api/messages/{id}` — no ownership check — `MessagesPage.tsx` |  | ⬜ |
| 117 | A07 | Inline reply sender spoofing — `replySenderId` from form, not JWT — `MessagesPage.tsx` |  | ⬜ |
| 118 | A01 | `PATCH /api/admin/users/{id}/toggle` — no ADMIN role check — `AdminController.java`, `AdminService.java` |  | ⬜ |
| 119 | A07 | Create User modal — role freely settable in request body — `AdminPage.tsx` |  | ⬜ |
| 120 | A04 | Password field in Create User modal rendered as plain text — `AdminPage.tsx` |  | ⬜ |
| 121 | A01 | Active/Inactive toggle button — no ADMIN role check — `AdminPage.tsx` |  | ⬜ |
| 122 | A04 | Password hash column — copyable MD5 — `AdminPage.tsx` |  | ⬜ |
| 123 | A09 | Audit Log "Clear All Logs" button — no confirmation, no ADMIN check — `AdminPage.tsx` |  | ⬜ |
| 124 | A01 | Patient prescription query — IDOR — `PrescriptionsPage.tsx` |  | ⬜ |
| 125 | A01 | Staff prescription query — all prescriptions returned to any role — `PrescriptionsPage.tsx` |  | ⬜ |
| 126 | A01 | Dispense button visible and functional for all roles — `PrescriptionsPage.tsx` |  | ⬜ |
| 127 | A07 | pharmacistId attributed from request body — `PrescriptionsPage.tsx` |  | ⬜ |
| 128 | A10 | Fail-open promote to READY on any validator exception — `RefillQueueService.java` |  | ⬜ |
| 129 | A10 | `catch (Throwable t)` around slip printing hides every error — `RefillQueueService.java` |  | ⬜ |
| 130 | A10 | `@Scheduled` worker swallows every exception in a single catch — `RefillQueueService.runWorker` |  | ⬜ |
| 131 | A10 | TOCTOU race on `dispense` — `RefillQueueService.dispense` |  | ⬜ |
| 132 | A10 | Swallowed `InterruptedException` — `RefillQueueService.dispense` |  | ⬜ |
| 133 | A10 | Validator dereferences `quantity` without a null check — `EligibilityValidator.java` |  | ⬜ |
| 134 | A10 | Schema permits the null that triggers the NPE — `V16__create_refill_requests.sql` |  | ⬜ |
| 135 | A10 | Slip write happens outside try/finally — `SlipPrinter.createSlip` |  | ⬜ |
| 136 | A10 | Raw exception text leaked in `failureReason` — `RefillRequestDto.java` + `GET /api/refills` |  | ⬜ |
| 137 | A10/A09 | Absolute filesystem path leaked in `tempSlipPath` — `RefillRequestDto.java` |  | ⬜ |
| 138 | A10 | `/refills/{id}/retry` has no max-retry guard — `RefillController.java` |  | ⬜ |
| 139 | A01/A10 | `/api/refills/**` mapped to `permitAll()` — `SecurityConfig.java` |  | ⬜ |
| 140 | A10 | "Request Refill" frontend button sends `quantity: null` on purpose — `PrescriptionsPage.tsx` |  | ⬜ |
| 141 | A10 | Dispense button on `RefillsPage` not disabled in-flight — `RefillsPage.tsx` |  | ⬜ |
| 142 | A10 | "Force Concurrent Dispense" demo button — `RefillsPage.tsx` |  | ⬜ |
| 143 | A10/A05 | `failureReason` rendered with `dangerouslySetInnerHTML` — `RefillsPage.tsx`, `AdminPage.tsx`, `ProfilePage.tsx` |  | ⬜ |
| 144 | A10 | [CWE-209] Stack-Trace Inspector panel — `RefillsPage.tsx` |  | ⬜ |
| 145 | A01 | AdminUserController under permitAll() — `AdminUserController.java` |  | ⬜ |
| 146 | A01 | Detail endpoint returns audit log with stack traces — `AdminUserService.java#getDetail` |  | ⬜ |
| 147 | A07 | PUT /admin/users/{id} mass assignment of every field except role — `AdminUserService.java#updateUser` |  | ⬜ |
| 148 | A01/A09 | DELETE /admin/users/{id} hard-delete of any account — `AdminUserController.java`, `AdminUserService.java#deleteUser`, `V17__cascade_user_fks.sql` |  | ⬜ |
| 149 | A07 | POST /admin/users/{id}/unlock neutralises brute-force lockout — `AdminUserService.java#unlock` |  | ⬜ |
| 150 | A02 | POST /admin/users/{id}/reset-password returns plaintext + writes it to audit log — `AdminUserService.java#resetPassword`, `ResetPasswordResponseDto.java` |  | ⬜ |
| 151 | A01/A07 | POST /admin/users/{id}/impersonate mints JWT for any user with no audit trail — `AdminUserService.java#impersonate`, `ImpersonationResponseDto.java` |  | ⬜ |
| 152 | A04/A09 | POST /admin/users/bulk-delete unbounded batch — `AdminUserController.java`, `AdminUserService.java#bulkDelete` |  | ⬜ |
| 153 | A06 | generateRandomPassword uses java.util.Random — `AdminUserService.java#generateRandomPassword` |  | ⬜ |
| 154 | A06 | AdminUserDetailDto + UserDto.passwordHash returned by detail endpoint — `AdminUserDetailDto.java`, `UserDto.java` (existing #79 family) |  | ⬜ |
| 155 | A01 | AdminRoute does not enforce role — `auth/AdminRoute.tsx` (frontend) |  | ⬜ |
| 156 | A01/A04 | ImpersonateBanner can be hidden by clearing one localStorage key — `components/admin/ImpersonateBanner.tsx` (frontend) |  | ⬜ |
| 157 | A01 | AdminUsersPage actions all toast OWASP tags but exercise no client-side validation — `pages/admin/AdminUsersPage.tsx` (frontend) |  | ⬜ |
| 158 | A03 | SQL Injection in audit log search — `AdminAuditService.java#search` |  | ⬜ |
| 159 | A01/A10 | GET /admin/logs/{id} returns raw stack trace — `AdminAuditController.java`, `AdminAuditService.java#findById` |  | ⬜ |
| 160 | A09 | DELETE /admin/logs/{id} — selective audit tampering — `AdminAuditController.java`, `AdminAuditService.java#deleteOne` |  | ⬜ |
| 161 | A03 | Audit log detail rendered via `dangerouslySetInnerHTML` — `AdminLogDetailPage.tsx` (frontend) |  | ⬜ |
| 162 | A03 | CSV / XML export built by string concatenation with no escaping — `AdminAuditService.java#exportLogs` / `toXmlElement` |  | ⬜ |
| 163 | A03 | XXE-vulnerable DocumentBuilderFactory — `AdminAuditService.java#renderXmlWithTemplate` |  | ⬜ |
| 164 | A03 | XML export download — UI flags it but lets it proceed — `AdminLogsPage.tsx#downloadExport` (frontend) |  | ⬜ |
| 165 | A03 | SQL injection via dashboard `since` parameter — `AdminOpsService.java#getDashboard` |  | ⬜ |
| 166 | A02 | /api/admin/health dumps JVM internals — `AdminOpsService.java#getHealth` |  | ⬜ |
| 167 | A02/A09 | Runtime config override via `PUT /api/admin/config/{key}` — `AdminOpsService.java#setConfig` |  | ⬜ |
| 168 | A03 | Arbitrary SQL execution via `POST /api/admin/maintenance/run-sql` — `AdminOpsService.java#runSql` |  | ⬜ |
| 169 | A04 | `POST /api/admin/maintenance/restart` calls `System.exit(0)` — `AdminOpsService.java#restart` |  | ⬜ |
| 170 | A03 | Command injection via `GET /api/admin/maintenance/backup?dbName=…` — `AdminOpsService.java#backup` |  | ⬜ |
| 171 | A03 | Run-SQL Console renders result cells with `dangerouslySetInnerHTML` — `AdminOpsPage.tsx` (frontend) |  | ⬜ |
| 172 | A04 | System Config dialog highlights but does not redact secrets — `AdminOpsPage.tsx` (frontend) |  | ⬜ |
| 173 | A01 | POST /admin/prescriptions/{id}/force-dispense bypasses pharmacist workflow — `AdminClinicalService.java#forceDispense` |  | ⬜ |
| 174 | A07 | PUT /admin/prescriptions/{id} mass-assigns patientId — `AdminClinicalService.java#updatePrescription` |  | ⬜ |
| 175 | A01 | POST /admin/refills/{id}/override skips queue + validator — `AdminClinicalService.java#overrideRefill` |  | ⬜ |
| 176 | A09 | DELETE /admin/medical-records/{id} hard-deletes clinical history — `AdminClinicalService.java#deleteMedicalRecord`, `V18__cascade_clinical_fks.sql` |  | ⬜ |
| 177 | A08 | POST /admin/lab-results/{id}/override-value mutates without amend flag — `AdminClinicalService.java#overrideLabResultValue` |  | ⬜ |
| 178 | A01/A07/A09 | // AdminOverridesPage exposes every action with one click — `AdminOverridesPage.tsx` (frontend) |  | ⬜ |
| 179 | A04 | POST /admin/doctors/{id}/verify-license trusts client claim — `AdminStaffService.java#verifyLicense` |  | ⬜ |
| 180 | A07 | PUT /admin/doctors/{id} mass-assigns every doctor field — `AdminStaffService.java#updateDoctor` |  | ⬜ |
| 181 | A05 | POST /admin/staff/onboard path traversal via license document upload — `AdminStaffService.java#onboardStaff` |  | ⬜ |
| 182 | A07 | POST /admin/staff/onboard creates any role from form data — `AdminStaffService.java#onboardStaff` |  | ⬜ |
| 183 | A05 | Uploaded filename rendered as HTML on the onboarding result panel — `AdminOnboardingPage.tsx` (frontend) |  | ⬜ |
| 184 | A04 | Doctor entity carries `licenseVerified` + `licenseDocumentPath` with no audit trail — `Doctor.java`, `V19__doctor_license_verification.sql` |  | ⬜ |
| 185 | A05 | POST /admin/broadcast stores raw HTML into Message.content — `AdminBroadcastService.java#broadcast` |  | ⬜ |
| 186 | A07 | Broadcast `senderId` taken from request body — `AdminBroadcastService.java#broadcast` |  | ⬜ |
| 187 | A04 | Broadcast fan-out unbounded — `AdminBroadcastService.java#broadcast` |  | ⬜ |
| 188 | A08/A09 | POST /admin/messages/{id}/redact overwrites content in place — `AdminBroadcastService.java#redact` |  | ⬜ |
| 189 | A05 | Broadcast preview pane renders the draft via `dangerouslySetInnerHTML` — `AdminBroadcastPage.tsx` (frontend) |  | ⬜ |
| 190 | A04 | Send-to-all default + Send button label updates dynamically — `AdminBroadcastPage.tsx` (frontend) |  | ⬜ |
| 191 | A01 | All seven `/api/doctor/**` controllers wired under `SecurityConfig.permitAll()` — `controller/Doctor*Controller.java` |  | ⬜ |
| 192 | A01 | `DoctorRoute.tsx` ships without `requiredRole` — `auth/DoctorRoute.tsx` (frontend) |  | ⬜ |
| 193 | A06 | `Sidebar.tsx` `dashboardPath` mapping is client-decoded role-based routing — `components/layout/Sidebar.tsx` (frontend) |  | ⬜ |
| 194 | A05 | `GET /api/doctor/patients` concatenates `q`, `active`, `recentDays` into raw SQL — `DoctorRosterService.java#listPatients` |  | ⬜ |
| 195 | A06 | `?recentDays=0` returns the entire patient table — `DoctorRosterService.java#listPatients` |  | ⬜ |
| 196 | A06 | Patient roster row exposes PII (insurance number, DOB, allergies) to every caller — `DoctorRosterEntryDto.java` |  | ⬜ |
| 197 | A01 | `GET /api/doctor/patients/{id}/chart` has no ownership / role check — `DoctorRosterController.java#getChart` |  | ⬜ |
| 198 | A09 | `GET /api/doctor/patients/{id}/timeline` returns raw `audit_logs.details` to a different principal — `DoctorRosterService.java#getTimeline` |  | ⬜ |
| 199 | A05 | `POST /api/doctor/patients/{id}/star` stores caller-supplied HTML which the roster table renders verbatim — `DoctorRosterService.java#starPatient` + `DoctorPatientsPage.tsx` |  | ⬜ |
| 200 | A08 | `POST /api/doctor/patients/{id}/handoff` issues HMAC-SHA1 token with hardcoded key and no expiry — `HandoffTokenIssuer.java` |  | ⬜ |
| 201 | A09 | Handoff issuance is not recorded in `audit_logs` — `DoctorRosterService.java#handoff` |  | ⬜ |
| 202 | A06 | In-memory `patientStars` map has no per-doctor scoping — `DoctorRosterService.java` |  | ⬜ |
| 203 | A09 | `DoctorPatientChartPage.tsx` Timeline tab renders raw audit details verbatim — `pages/doctor/DoctorPatientChartPage.tsx` (frontend) |  | ⬜ |
| 204 | A03 | Freemarker SSTI — `TemplateRenderer.java#renderInline` + `DoctorNoteService.java#create` |  | ⬜ |
| 205 | A03 | Freemarker template-path traversal — `TemplateRenderer.java#renderByName` + `DoctorNoteService.java#create` |  | ⬜ |
| 206 | A03 | XXE via `DocumentBuilderFactory.newInstance()` defaults — `DoctorNoteService.java#importXml` |  | ⬜ |
| 207 | A05 | Note `renderedHtml` rendered with `dangerouslySetInnerHTML` — `DoctorNoteDetailPage.tsx` (frontend) |  | ⬜ |
| 208 | A08 | `PUT /api/doctor/notes/{id}` overwrites in place — `DoctorNoteService.java#update` |  | ⬜ |
| 209 | A09 | `DELETE /api/doctor/notes/{id}` hard deletes, no audit row — `DoctorNoteService.java#delete` |  | ⬜ |
| 210 | A08 | JWT `alg: none` accepted on co-sign — `JwtNoneVerifier.java` |  | ⬜ |
| 211 | A10 | Template rendering exceptions surface verbatim — `TemplateRenderer.java` (`DEBUG_HANDLER` + `<pre class="render-error">`) |  | ⬜ |
| 212 | A03 | `NoteEditor.tsx` ships a default SSTI payload — `components/doctor/NoteEditor.tsx` (frontend) |  | ⬜ |
| 213 | A03 | `DoctorNotesPage.tsx` Import XML button accepts attacker-controlled XML — `pages/doctor/DoctorNotesPage.tsx` |  | ⬜ |
| 214 | A03 | `POST /api/doctor/lab-orders` fetches caller-supplied `customQueryUrl` server-side — `DoctorLabService.java#createOrder` + `ExternalCatalogueClient.java` |  | ⬜ |
| 215 | A05 | `catalogueResponse` rendered with `dangerouslySetInnerHTML` — `DoctorLabsPage.tsx` (frontend) |  | ⬜ |
| 216 | A08 | `POST /api/doctor/lab-orders/{id}/sign` uses MD5 + hardcoded key — `DoctorLabService.java#signOrder` |  | ⬜ |
| 217 | A04 | Lab signing key + hardcoded weak primitive — `DoctorLabService.java` |  | ⬜ |
| 218 | A05 | Imaging upload writes `getOriginalFilename()` verbatim — `DoctorLabService.java#upload` |  | ⬜ |
| 219 | A02 | `POST /imaging/upload` echoes `Content-Type` verbatim on subsequent GET — `DoctorLabService.java#upload` + `streamImaging` + `DoctorLabController.java#getImaging` |  | ⬜ |
| 220 | A03 | `POST /imaging/import-url` plain SSRF — `DoctorLabService.java#importUrl` |  | ⬜ |
| 221 | A02 | Basename of source URL used as on-disk filename — `DoctorLabService.java#importUrl` |  | ⬜ |
| 222 | A09 | Sign / upload / import-url all silent — `DoctorLabService.java` |  | ⬜ |
| 223 | A10 | Verbose fetch / upload errors — `ExternalCatalogueClient.java` + `DoctorLabService.java` |  | ⬜ |
| 224 | A02 | `LabOrderForm.tsx` ships SSRF payloads in placeholder text — `components/doctor/LabOrderForm.tsx` (frontend) |  | ⬜ |
| 225 | A08 | `PrescriptionSigner` uses MD5 with hardcoded key — `service/PrescriptionSigner.java` |  | ⬜ |
| 226 | A02/A04 | Sign response returns the plaintext signing key — `service/DoctorPrescribingService.java#sign` + `PrescriptionSignatureDto.java` |  | ⬜ |
| 227 | A08 | `GET /prescriptions/{id}/pdf` returns an unsigned PDF — `service/DoctorPrescribingService.java#generatePdf` |  | ⬜ |
| 228 | A03 | `POST /api/doctor/prescriptions` POSTs to caller-supplied `pharmacyCallbackUrl` — `DoctorPrescribingService.java#create` + `#sendPharmacyNotice` |  | ⬜ |
| 229 | A03 | `POST /drug-interactions/check` SSRF on `catalogueUrl` + Nashorn `eval()` RCE — `DoctorPrescribingService.java#drugInteractions` |  | ⬜ |
| 230 | A08 | Co-sign accepts JWT with `alg: none` — `DoctorPrescribingService.java#coSign` |  | ⬜ |
| 231 | A07 | Prescription identities all from request body — `DoctorPrescribingService.java#create` |  | ⬜ |
| 232 | A09 | Sign + co-sign + create-with-callback all silent — `DoctorPrescribingService.java` |  | ⬜ |
| 233 | A10 | `sendPharmacyNotice` swallows all downstream errors — `DoctorPrescribingService.java` |  | ⬜ |
| 234 | A02 | Plaintext signing key surfaced in the UI — `PrescriptionSigner.tsx` (frontend) |  | ⬜ |
| 235 | A03 | `DrugInteractionPanel.tsx` default placeholder + result helpers — `DrugInteractionPanel.tsx` (frontend) |  | ⬜ |
> rows 236–244 below: 🚫 wont-fix — Telemedicine tab removed.

| 236 | A02 | Join token spliced into room URL query string — `DoctorSessionService.java#create` | (removed) | 🚫 |
| 237 | A02 | Public iCal feed — no auth required — `DoctorSessionController.java#calendarFeed` | (removed) | 🚫 |
| 238 | A02 | iCal SUMMARY + DESCRIPTION carry PHI verbatim — `DoctorSessionService.java#icalFeed` | (removed) | 🚫 |
| 239 | A01 | iCal feed scoped only by `?doctorId` query param — `DoctorSessionController.java#calendarFeed` | (removed) | 🚫 |
| 240 | A03 | `POST /api/doctor/sessions/{id}/recording` SSRF on `recordingUrl` — `DoctorSessionService.java#attachRecording` | (removed) | 🚫 |
| 241 | A02 | basename of `recordingUrl` used as on-disk filename — `DoctorSessionService.java#attachRecording` | (removed) | 🚫 |
| 242 | A09 | Session create / end / recording-attach all silent — `DoctorSessionService.java` | (removed) | 🚫 |
| 243 | A02 | `TelemedicineRoom.tsx` iframe Referer leak — `components/doctor/TelemedicineRoom.tsx` (frontend) | (removed) | 🚫 |
| 244 | A02 | No `Content-Disposition` / `Referrer-Policy` on calendar / session responses — `DoctorSessionController.java` | (removed) | 🚫 |
| 245 | A08 | Java deserialisation RCE on referral inbox — `DoctorReferralService.java#decodeBundle` + `dto/ReferralBundle.java` |  | ⬜ |
| 246 | A08 | `accept` writes a `MedicalRecord` from the deserialised bundle with no provenance — `DoctorReferralService.java#accept` |  | ⬜ |
| 247 | A02 | `GET /api/doctor/ai/model-info` returns the AI API key in clear — `DoctorAIService.java#modelInfo` | (removed) | 🚫 |
| 248 | A04 | AI API key hardcoded in source — `DoctorAIService.java` | (removed) | 🚫 |
| 249 | A03 | `POST /api/doctor/ai/suggest` SSRF + PHI exfil + key leak on outbound — `DoctorAIService.java#suggest` + `postJsonAndReadResponse` | (removed) | 🚫 |
| 250 | A08 | `POST /api/doctor/ai/summarize-record` stores LLM response as `aiVerified=true` MedicalRecord — `DoctorAIService.java#summarizeRecord` | (removed) | 🚫 |
| 251 | A05 | `AIAssistantPanel.tsx` renders LLM response with `dangerouslySetInnerHTML` — `components/doctor/AIAssistantPanel.tsx` (frontend) | (removed) | 🚫 |
| 252 | A05 | `ReferralBundleViewer.tsx` renders bundle summary as HTML — `components/doctor/ReferralBundleViewer.tsx` (frontend) |  | ⬜ |
| 253 | A09 | No audit on deserialisation, accept (referral half only — AI half removed) — `DoctorReferralService.java` |  | ⬜ |
| 254 | A10 | Failed deserialisation silently returns null — `DoctorReferralService.java#decodeBundle` |  | ⬜ |
| 255 | A02 | AI gateway key sent on every outbound POST even to caller-controlled URLs — `DoctorAIService.java#postJsonAndReadResponse` | (removed) | 🚫 |
| 256 | A06 | `POST /api/doctor/appointments/{id}/approve` has no double-book check — `DoctorAppointmentService.java#approve` |  | ⬜ |
| 257 | A07 | `actorDoctorId` from request body — `DoctorAppointmentService.java#approve` + `#decline` |  | ⬜ |
| 258 | A05 | Decline reason rendered with `dangerouslySetInnerHTML` on the inbox — `DoctorAppointmentsPage.tsx` + service |  | ⬜ |
| 259 | A08 | `POST /{id}/reschedule` overwrites `requestedDate` in place — `DoctorAppointmentService.java#reschedule` |  | ⬜ |
| 260 | A08 | `POST /{id}/complete` auto-creates a `MedicalRecord` with caller-supplied notes — `DoctorAppointmentService.java#complete` |  | ⬜ |
| 261 | A06 | `POST /{id}/no-show` has no rate limit — `DoctorAppointmentService.java#noShow` |  | ⬜ |
| 262 | A06/A09 | `POST /bulk-status` unbounded `ids` list, single audit row — `DoctorAppointmentService.java#bulkStatus` |  | ⬜ |
| 263 | A06 | CSV import accepts unbounded row count — `DoctorAppointmentService.java#importCsv` |  | ⬜ |
| 264 | A03 | CSV export — formula injection on `notes` cells — `DoctorAppointmentService.java#exportCsv` |  | ⬜ |
| 265 | A03 | CSV import passes formula payloads through verbatim — `DoctorAppointmentService.java#importCsv` |  | ⬜ |
| 266 | A02 | `GET /export.csv` served without `Content-Disposition: attachment` — `DoctorAppointmentController.java#exportCsv` |  | ⬜ |
| 267 | A05 | `GET /api/doctor/appointments?q=` raw SQL concat — `DoctorAppointmentService.java#list` |  | ⬜ |
| 268 | A01 | `GET /api/doctor/appointments/conflicts` returns every doctor's overlaps with patient PII — `DoctorAppointmentService.java#conflicts` |  | ⬜ |
| 269 | A01 | `POST /api/appointments` — doctorId from client state not verified server-side — `DoctorAppointmentsPage.tsx` |  | ⬜ |
