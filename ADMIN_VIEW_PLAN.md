# Admin View — Redesign Plan

> Educational OWASP Top 10:2025 project. Every new endpoint below intentionally
> introduces vulnerabilities so they can be demonstrated and exploited. This is a
> **planning document** — nothing here is implemented yet.

---

## 1. Why the Current Admin View Looks Like a Patient View

The current `AdminController` only exposes 6 thin endpoints and most of them
overlap with what a regular patient could reasonably do or see:

| Existing endpoint | Problem |
|---|---|
| `GET  /api/admin/users` | A flat user list — no filtering, no per-role breakdown, no impersonation tools. Looks like a directory anyone could read. |
| `POST /api/admin/users` | Generic user-create form — identical in shape to public registration. Nothing in it screams "admin only". |
| `GET  /api/admin/config` | Single dump of properties — no per-section view, no system health, no toggles. |
| `POST /api/admin/logs/clear` | Audit purge, but no audit *exploration* (search, filter, replay). |
| `GET  /api/admin/logs` | Returns everything; admin has no way to slice it. |
| `PATCH /api/admin/users/{id}/toggle` | Active flag only — no password reset, no role change, no force-logout, no impersonation. |

Missing entirely: doctor onboarding, license verification, system-wide stats
dashboard, prescription audit, refill backlog overrides, broadcast messages,
database/maintenance ops, role assignment, account unlock, session management,
backup & restore, feature flags.

---

## 2. Goals of the Redesign

1. **Admin-only surface area** — endpoints a `PATIENT` / `DOCTOR` / `LAB_TECH`
   would never legitimately call (impersonation, license override, DB queries,
   broadcast, feature flags).
2. **Operational dashboard feel** — summary cards, drill-downs, system health,
   recent events, account lockout panel.
3. **Each new endpoint must teach at least one OWASP Top 10:2025 category** —
   ideally a category not already heavily covered in `AdminController`
   (currently A01, A04, A07, A09 are over-represented).
4. **Stay consistent with project conventions**: English-only comments,
   `[A0X]` tags inline, `VULNERABLE_CONFIG.md` updated after implementation.

---

## 3. Target Admin Feature Set

Grouped into **6 modules**. Each module has a primary OWASP category to
demonstrate, plus secondary categories that arise naturally.

### Module A — User & Account Management
Things a real hospital admin actually does to user accounts.

| Endpoint | Purpose |
|---|---|
| `GET    /api/admin/users` *(existing — keep)* | List, but extend with `?role=`, `?active=`, `?lockedOnly=` filters. **Cross-cutting:** `MessagesPage.tsx:112` also consumes this — any breaking change to the response shape must update that call site. |
| `GET    /api/admin/users/{id}` | Full user profile incl. last-login IP, failed-login history |
| `POST   /api/admin/users` *(existing — keep)* | Create any role |
| `PUT    /users/{id}/role` *(existing on `UserController` — keep)* | Already wired to the Change Role modal in `AdminPage.tsx:235`. Stays as the role-mutation endpoint; the new `PUT /api/admin/users/{id}` below handles *other* field mass-assignment without superseding it. |
| `PUT    /api/admin/users/{id}` | Update any field **except `role`** (which stays on `PUT /users/{id}/role` for UI compatibility). Still mass-assignable for `passwordHash`, `lockedUntil`, `email`, `active`, etc. |
| `DELETE /api/admin/users/{id}` | Hard delete |
| `POST   /api/admin/users/{id}/unlock` | Reset `failedLoginAttempts`, clear `lockedUntil` |
| `POST   /api/admin/users/{id}/reset-password` | Set arbitrary password — returns plaintext in response |
| `POST   /api/admin/users/{id}/impersonate` | Issue a JWT for that user, return it to caller |
| `POST   /api/admin/users/bulk-delete` | Body: `{"ids":[1,2,3]}` |

### Module B — Clinical Staff Onboarding
Approving doctors / lab techs / pharmacists. None of this exists today.

| Endpoint | Purpose |
|---|---|
| `POST  /api/admin/doctors/verify-license` | Mark `Doctor.licenseVerified=true` — accepts a `licenseNumber` from the body, no real verification |
| `PUT   /api/admin/doctors/{id}` | Edit specialty, hospital, license #, phone |
| `POST  /api/admin/staff/onboard` | One-shot create User + role-specific entity (Doctor/Patient/etc.) — multipart with `licenseDocument` file upload |

### Module C — System & Operations Dashboard
The "is everything OK?" panel.

| Endpoint | Purpose |
|---|---|
| `GET  /api/admin/dashboard` | Aggregated counts: users by role, appointments by status, prescriptions, refill backlog, failed logins last 24h |
| `GET  /api/admin/health` | Custom health endpoint (not actuator) — DB latency, disk, JVM mem |
| `GET  /api/admin/config` *(existing — keep)* | Raw env dump |
| `PUT  /api/admin/config/{key}` | **Runtime mutation** of a property (e.g., flip `app.debug.enabled`) |
| `POST /api/admin/maintenance/run-sql` | Body: `{"sql":"..."}` — executes arbitrary SQL with admin DB user |
| `POST /api/admin/maintenance/restart` | Calls `System.exit(0)` so the supervisor restarts the JVM |
| `GET  /api/admin/maintenance/backup` | Streams a `mysqldump` of the DB to the response |

### Module D — Audit & Forensics
Audit log is currently read-all / delete-all. Admin needs slicing tools.

| Endpoint | Purpose |
|---|---|
| `GET  /api/admin/logs` *(existing — keep)* | List, extend with `?userId=`, `?action=`, `?from=`, `?to=`, `?q=` (free text in `details`) |
| `GET  /api/admin/logs/{id}` | Single log entry with full stack trace |
| `GET  /api/admin/logs/export` | CSV/JSON export of filtered set |
| `POST /api/admin/logs/clear` *(existing — keep)* | Hard purge |
| `DELETE /api/admin/logs/{id}` | Selective deletion of a single entry |

### Module E — Clinical Overrides
Admin-level overrides on data normally guarded by doctor/pharmacist workflows.

| Endpoint | Purpose |
|---|---|
| `POST   /api/admin/prescriptions/{id}/force-dispense` | Bypass pharmacist workflow |
| `PUT    /api/admin/prescriptions/{id}` | Edit any field including `dosage`, `medication`, `patientId` |
| `POST   /api/admin/refills/{id}/override` | Force-approve a refill request |
| `DELETE /api/admin/medical-records/{id}` | Hard-delete a clinical record |
| `POST   /api/admin/lab-results/{id}/override-value` | Mutate the numeric value of a lab result |

### Module F — Communications
Admin-level messaging.

| Endpoint | Purpose |
|---|---|
| `POST /api/admin/broadcast` | Body: `{"subject":"...","html":"...","roles":["PATIENT"]}` — sends a Message to every account matching `roles`. `html` rendered as-is on the recipient UI. |
| `POST /api/admin/messages/{id}/redact` | Replace any message body — no audit trail of the original |

---

## 4. Vulnerabilities per Module

Each endpoint gets at least one OWASP 2025 tag. Goal is to **broaden coverage**
beyond the four (A01/A04/A07/A09) the current `AdminController` already demos.

### Module A — Users & Accounts

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `GET /users` (filters) | SQL fragment in `?role=` concatenated into JPQL | **A03** (Injection / Supply Chain) |
| `GET /users/{id}` | IDOR — no admin role enforcement, plus returns `passwordHash` | **A01** |
| `PUT /users/{id}` | Mass assignment of `role`, `passwordHash`, `lockedUntil` | **A07** |
| `DELETE /users/{id}` | No role check, no soft-delete | **A01** + **A09** (no record of who deleted) |
| `POST /users/{id}/unlock` | No role check + no rate limit on lockout reset → bypass brute-force protection | **A07** |
| `POST /users/{id}/reset-password` | Returns the new plaintext password in JSON; also logged in audit details | **A02** (Cryptographic Failure / Misconfig — leaks secret in logs) |
| `POST /users/{id}/impersonate` | Issues a real JWT for another user, no MFA, no audit, token has 24h TTL | **A01** + **A07** |
| `POST /users/bulk-delete` | Accepts unbounded ID list — DoS via huge batch | **A04** (Insecure Design — no batch caps) |

### Module B — Clinical Staff Onboarding

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /doctors/verify-license` | Client says "verified=true"; no external license API check | **A04** (Insecure Design — trusting client claim) |
| `PUT /doctors/{id}` | Mutates `licenseNumber` without re-verification | **A04** |
| `POST /staff/onboard` (multipart) | `licenseDocument` saved with original filename → path traversal `../../etc/passwd`. No MIME / size validation | **A03** (file-based injection vector) + **A02** (misconfigured upload dir) |

### Module C — System & Ops

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `GET /dashboard` | Counts joined via raw SQL with `?since=` concatenation | **A03** |
| `GET /health` | Returns full JVM properties incl. `java.class.path`, agent flags | **A02** (Misconfig — over-exposure) |
| `PUT /config/{key}` | Mutates `app.debug.enabled`, `app.detailed.errors` at runtime — flips error verbosity, disables interceptor logging | **A02** + **A09** (caller can turn off audit logging) |
| `POST /maintenance/run-sql` | Direct `EntityManager.createNativeQuery(body.sql).executeUpdate()` | **A03** (textbook SQL injection / RCE-equivalent) |
| `POST /maintenance/restart` | `System.exit(0)` callable by anyone → availability attack | **A04** (Insecure Design — no admin gate, no rate limit) |
| `GET /maintenance/backup` | Spawns `mysqldump` via `Runtime.exec("mysqldump ... " + dbName)` with `dbName` from query string | **A03** (OS command injection) |

### Module D — Audit & Forensics

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `GET /logs` (filters) | `?q=` concatenated into a `LIKE` clause | **A03** |
| `GET /logs/{id}` | Returns stack trace verbatim incl. SQL fragments | **A10** (Mishandling Exceptional Conditions — exception details surfaced to caller) |
| `GET /logs/export` | XXE-vulnerable XML export option (`?format=xml` using `DocumentBuilderFactory` with external entities enabled) | **A03** |
| `DELETE /logs/{id}` | Selective tampering with audit trail | **A09** |

### Module E — Clinical Overrides

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /prescriptions/{id}/force-dispense` | No role check; also no `EligibilityValidator` call | **A01** + **A04** |
| `PUT /prescriptions/{id}` | Mass assignment incl. `patientId` — re-target a prescription | **A07** |
| `POST /refills/{id}/override` | Skips queue; sets `RefillStatus.APPROVED` directly | **A01** |
| `DELETE /medical-records/{id}` | Hard delete of clinical record, no audit entry | **A09** |
| `POST /lab-results/{id}/override-value` | Mutates `value` and `referenceRange` without flagging "amended" | **A08** (Software & Data Integrity Failures) |

### Module F — Communications

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /broadcast` | `html` field stored verbatim → stored XSS for every recipient who renders the message | **A03** (Injection — XSS variant) |
| `POST /messages/{id}/redact` | Overwrites without preserving original — audit trail integrity loss | **A08** + **A09** |

### Summary — Coverage Matrix

| OWASP 2025 | Existing admin endpoints | New endpoints added by this plan |
|---|---|---|
| A01 Broken Access Control | 4 | 4 |
| A02 Security Misconfiguration | 0 | 4 |
| A03 Supply Chain / Injection | 0 | 7 |
| A04 Insecure Design | 1 | 5 |
| A07 Identification & Auth Failures | 1 | 4 |
| A08 Software & Data Integrity | 0 | 2 |
| A09 Security Logging Failures | 1 | 4 |
| A10 Mishandling Exceptional Conditions | 0 | 1 |

This is the main argument for the redesign — current admin surface only hits 4
categories, the proposed surface hits 8.

---

## 5. UI Changes

The UI lives in a separate repo at `/Users/jelenarastovic/Downloads/mediconnect-frontend`.

**Stack** (confirmed by inspecting the repo): React 18 + Vite + TypeScript +
TailwindCSS, react-router-dom v7, TanStack Query, axios (`src/api/axiosInstance.ts`),
Radix-UI-derived primitives wrapped in `src/components/ui/*` (Card, Dialog,
Button, Input, Select, Table, Badge, Separator, etc.), Lucide icons, Recharts.

### 5.1 Why the Current Admin Page Feels Like a Patient Page

Three concrete reasons, all visible in the existing code:

1. **Shared sidebar with 8 patient items + 1 admin item.**
   `src/components/layout/Sidebar.tsx` (`navItems` array, line 29) lists
   Dashboard, Appointments, Medical Records, Lab Results, Messages,
   Prescriptions, Refills, Profile — *then* appends Admin as the 9th item.
   The admin sees the full patient-shaped nav with one extra link. The
   existing `adminOnly` flag (line 71) only filters that extra item *in*
   for admins; nothing filters the patient items *out*.
2. **Shared layout.** `src/components/layout/AppLayout.tsx` wraps every
   page (patient and admin). No admin-specific shell, header, or accent.
3. **One-page admin.** `src/pages/AdminPage.tsx` is a single page with two
   tabs (Users / Audit Logs) plus a Refill Queue card, three modals
   (Create User, Change Role, System Config). No system ops, no overrides,
   no broadcast, no impersonation, no staff onboarding.

### 5.2 Screen Inventory

Replace the single `AdminPage.tsx` with a top-level **Admin Console** that
has its own sidebar. Each console section maps 1:1 to a backend module
from section 3.

| Section | Route | Page component | Backend module |
|---|---|---|---|
| **Dashboard** | `/admin` | `pages/admin/AdminDashboardPage.tsx` | C |
| **Users** | `/admin/users`, `/admin/users/:id` | `AdminUsersPage.tsx`, `AdminUserDetailPage.tsx` | A |
| **Staff Onboarding** | `/admin/onboarding` | `AdminOnboardingPage.tsx` | B |
| **System & Ops** | `/admin/ops` | `AdminOpsPage.tsx` (sub-tabs: Config, Health, Maintenance) | C |
| **Audit Logs** | `/admin/logs`, `/admin/logs/:id` | `AdminLogsPage.tsx`, `AdminLogDetailPage.tsx` | D |
| **Clinical Overrides** | `/admin/overrides` | `AdminOverridesPage.tsx` (tabs: Prescriptions / Refills / Labs) | E |
| **Broadcast** | `/admin/broadcast` | `AdminBroadcastPage.tsx` | F |

The existing `src/pages/AdminPage.tsx` will be split — its Users tab content
moves into `AdminUsersPage.tsx`, the Audit Logs tab into `AdminLogsPage.tsx`,
the Refill Queue card (with its `dangerouslySetInnerHTML` failureReason
sink — the only A10 demo on the admin surface today) becomes a
dashboard widget in `AdminDashboardPage.tsx`. The Create User / Change
Role / System Config modals carry over into their respective new pages.
The original file then goes away.

### 5.3 Layout & Routing Changes

| File | Change |
|---|---|
| `src/components/layout/Sidebar.tsx` | When `user?.role === 'ADMIN'`, render the **admin nav set** instead of the patient set. The `adminOnly` flag pattern stays for the A01 demo (server still doesn't enforce), but the patient items get hidden so the admin view stops looking like a patient view. |
| `src/components/layout/AppLayout.tsx` | Accept an optional `variant="admin"` prop that switches header text to "Admin Console" and applies an accent border. No real auth check — same A01 client-side-only pattern as today. |
| **New** `src/components/layout/AdminSidebar.tsx` | Cleaner split: separate component used only by admin pages. Keeps the patient `Sidebar.tsx` untouched. Recommended over a `variant` prop. |
| **New** `src/auth/AdminRoute.tsx` | Mirrors existing `ProtectedRoute.tsx`. **Deliberately does not pass `requiredRole="ADMIN"`** — the existing `ProtectedRoute` already supports that prop, and using it would *fix* the A01 demo. The new wrapper exists for routing organisation, not enforcement; the A01 weakness stays intact (any authenticated user navigating to `/admin/*` reaches it). |
| `src/App.tsx` | Replace the single `/admin` route at line 42 with the new `/admin/*` routes under `<AdminRoute>`. Keep the existing `[A01]` comment block above. |

### 5.4 New Components

Reuse existing `ui/*` primitives wherever possible. New pieces:

- `pages/admin/*` — seven page components (see 5.2).
- `components/admin/AdminSidebar.tsx` — admin nav (Dashboard, Users, Staff,
  Ops, Logs, Overrides, Broadcast).
- `components/admin/UserDetailDrawer.tsx` — full profile, last-login IP,
  Impersonate / Reset Password / Unlock / Delete buttons.
- `components/admin/ImpersonateBanner.tsx` — sticky top banner visible
  whenever the JWT was minted via the impersonation endpoint. Needed so the
  A01/A07 demo is **legible** — without it students don't notice the session swap.
- `components/admin/RunSqlConsole.tsx` — textarea + Execute + result grid.
- `components/admin/ConfigEditor.tsx` — key/value list with inline edit.
- `components/admin/LogDetailModal.tsx` — renders stack trace.
- `components/admin/BroadcastComposer.tsx` — textarea with a "Preview"
  pane (preview rendered via `dangerouslySetInnerHTML`). Recipient-side
  XSS sink already exists at `pages/MessagesPage.tsx:293` — broadcast
  just needs to write HTML into `Message.content` and the existing
  inbox renderer fires the payload. No new recipient sink needed.
- `api/admin.ts` — typed client wrappers for the new endpoints (mirrors
  the existing `api/refills.ts` pattern).

### 5.5 UI-Layer Vulnerabilities

**Existing UI vulnerabilities — do not duplicate, lean on them:**

| Location | Vulnerability | OWASP |
|---|---|---|
| `auth/AuthContext.tsx` | JWT + user persisted in `localStorage`; role decoded client-side from JWT payload without signature check | **A06** |
| `auth/ProtectedRoute.tsx` | Supports `requiredRole`, but `/admin` route at `App.tsx:42` deliberately omits it | **A01** |
| `components/layout/Sidebar.tsx` (line 71) | `adminOnly` flag filtered client-side only | **A01** |
| `pages/AdminPage.tsx` (line 97) | Refill `failureReason` rendered via `dangerouslySetInnerHTML` | **A05** + **A10** |
| `pages/MessagesPage.tsx` (line 293) | Message `content` rendered via `dangerouslySetInnerHTML` — recipient-side XSS sink already in place | **A03** |
| `pages/MessagesPage.tsx` (line 112) | Calls `GET /admin/users` to fill recipient dropdown — non-admin code depends on the admin endpoint | **A01** |
| `pages/RegisterPage.tsx` (line 152) | Public registration form exposes `ADMIN` role option — second path to A07 escalation that exists *outside* the admin module | **A07** |
| `pages/ProfilePage.tsx`, `pages/RefillsPage.tsx` | Refill `failureReason` `dangerouslySetInnerHTML` (same pattern as AdminPage) | **A05** + **A10** |

**New UI vulnerabilities added by this plan:**

| UI surface | Vulnerability | OWASP |
|---|---|---|
| `UserDetailDrawer` | Renders `passwordHash`, `lockedUntil`, `failedLoginAttempts` in plain DOM (no masking). Extends existing pattern from current `AdminPage.tsx`. | **A02** |
| `ImpersonateButton` | Replaces the JWT in `localStorage` with the impersonation token under the **same** key. No banner if `ImpersonateBanner` is missing → admin silently "becomes" the victim. | **A01** + **A04** |
| Reset-Password modal | Displays the new plaintext password and copies it to clipboard; no auto-clear. Reuses the existing "click to copy" pattern from `copyHash()` in `AdminPage.tsx`. | **A02** |
| `OnboardingWizard` | Filename of uploaded license rendered via `dangerouslySetInnerHTML` → `<img onerror>` filename = stored XSS for next admin who views the staff list. | **A03** |
| `RunSqlConsole` | Result cells rendered with `dangerouslySetInnerHTML` so `SELECT '<script>...</script>'` executes in the admin session. | **A03** |
| `LogDetailModal` | `details` field (stack traces, request bodies) rendered with `dangerouslySetInnerHTML`. | **A03** + **A10** |
| Audit-log Export dropdown | "XML" option calls the XXE-vulnerable backend export endpoint; UI shows no warning that XML resolves external entities. | **A03** |
| `BroadcastComposer` | Preview pane uses `dangerouslySetInnerHTML`. **Recipient sink already exists** at `MessagesPage.tsx:293` — writing HTML into `Message.content` triggers the existing renderer; no new recipient code needed. | **A03** |
| `BroadcastComposer` | "Send to all roles" checkbox defaults to true; no confirm dialog before broadcast → one misclick spams every account. | **A04** |
| `OverridesLabsTab` | Lab-value edit submits silently — no "amended" badge anywhere in the UI. Data-integrity loss is invisible to clinicians. | **A08** |
| Error toasts (global) | `axios` interceptor renders `err.response.data.message` (which is the backend stack trace under A10) directly in a toast via `dangerouslySetInnerHTML`. | **A10** |
| Global — CSP / `X-Frame-Options` | `vite.config.ts` ships no CSP; admin console embeddable in attacker iframe → clickjacking on Impersonate / Clear-Logs / Run-SQL buttons. | **A02** |

### 5.6 UX Signals to Keep the Demos Teachable

Vulnerabilities only land as lessons if students can **see** them. Two rules:

1. **No silent failures.** Every override / impersonation / log-clear shows
   a `useToast()` message naming the OWASP tag it just exercised — the
   existing `AdminPage.tsx` already does this (`'[A07] User created — role
   accepted from request body'`). Keep this pattern for every new action.
2. **Visible `ImpersonateBanner`.** Even though the impersonation flow is
   insecure by design, the banner makes the demo legible. Without it
   students wouldn't notice the session swap.

---

## 5.7 Cross-Repo Dependencies

Three places where backend changes ripple into the UI (or vice versa) and
need coordinated edits:

1. **`GET /admin/users` is consumed twice.** Used by `AdminPage.tsx` (will
   move to `AdminUsersPage.tsx`) **and** by `MessagesPage.tsx:112` to
   populate the recipient dropdown. Any change to filter params or response
   shape needs to update both call sites — or, cleaner, give `MessagesPage`
   a dedicated `GET /users` endpoint and let the admin route diverge freely.
2. **Role mutation endpoint stays on `UserController`.** `PUT /users/{id}/role`
   is the one Change-Role flow wired into the existing UI. The plan keeps
   this endpoint as-is; new admin work (Module A `PUT /api/admin/users/{id}`)
   updates *other* fields. Pick one path before implementing — splitting role
   updates across two endpoints is confusing.
3. **`RegisterPage.tsx` is a parallel A07 escalation surface.** Public
   registration already exposes the ADMIN dropdown — admin redesign should
   **not** accidentally close this; both paths should remain demoable.

---

## 6. File Layout Changes

### 6.1 Backend (`mediconnect-api`)

```
controller/
  AdminController.java              ← split into:
  AdminUserController.java          (Module A)
  AdminStaffController.java         (Module B)
  AdminOpsController.java           (Module C)
  AdminAuditController.java         (Module D)
  AdminClinicalController.java      (Module E)
  AdminBroadcastController.java     (Module F)

service/
  AdminService.java                 ← split into:
  AdminUserService.java
  AdminStaffService.java
  AdminOpsService.java
  AdminAuditService.java
  AdminClinicalService.java
  AdminBroadcastService.java

dto/
  AdminDashboardDto.java
  AdminUserDetailDto.java
  ImpersonationTokenDto.java
  BroadcastRequestDto.java
  RunSqlRequestDto.java
  LicenseVerificationDto.java

security/
  (none of these endpoints get @PreAuthorize — that is the point)
```

`SecurityConfig.java` keeps `/api/admin/**` on `permitAll()` (existing A01).
No change there; the new endpoints rely on the same misconfiguration.

### 6.2 Frontend (`mediconnect-frontend`)

```
src/
  pages/
    AdminPage.tsx                   ← DELETE (content split below)
    admin/                          ← NEW directory
      AdminDashboardPage.tsx
      AdminUsersPage.tsx
      AdminUserDetailPage.tsx
      AdminOnboardingPage.tsx
      AdminOpsPage.tsx
      AdminLogsPage.tsx
      AdminLogDetailPage.tsx
      AdminOverridesPage.tsx
      AdminBroadcastPage.tsx

  components/
    layout/
      Sidebar.tsx                   ← hide patient nav items for ADMIN
      AppLayout.tsx                 ← (optional) accept variant="admin"
    admin/                          ← NEW directory
      AdminSidebar.tsx
      UserDetailDrawer.tsx
      ImpersonateBanner.tsx
      RunSqlConsole.tsx
      ConfigEditor.tsx
      LogDetailModal.tsx
      BroadcastComposer.tsx

  auth/
    AdminRoute.tsx                  ← NEW (mirrors ProtectedRoute.tsx;
                                      deliberately omits requiredRole
                                      so the A01 demo stays alive — the
                                      existing ProtectedRoute already
                                      supports requiredRole, using it
                                      would close the demo)

  api/
    admin.ts                        ← NEW (typed wrappers for /api/admin/**)
```

The shared `components/ui/*` primitives (Card, Dialog, Button, Input, Select,
Table, Badge, Separator) are reused as-is — no new design-system work needed.

---

## 7. Implementation Phases

Suggested order — each phase is independently demoable, ships **backend
endpoints + matching UI screens together**, and leaves both repos runnable.

| Phase | Module | Backend deliverable | UI deliverable |
|---|---|---|---|
| 0 | Shell | — | In `Sidebar.tsx` extend `resolvedNavItems` filter (line 71) so patient items are filtered *out* when `user?.role === 'ADMIN'` (today only the `adminOnly` item is filtered *in*); add `AdminRoute.tsx` (no `requiredRole` — A01 demo preserved) + `AdminSidebar.tsx`; replace `/admin` route in `App.tsx:42` with `/admin/*` subtree; delete `pages/AdminPage.tsx` after later phases absorb its content |
| 1 | A — User & Account Management | New endpoints under `/api/admin/users/*` | `AdminUsersPage.tsx`, `UserDetailDrawer.tsx`, `ImpersonateBanner.tsx`, Reset Password / Unlock / Delete flows |
| 2 | D — Audit & Forensics | Filter params on `/logs`, `/logs/{id}`, `/logs/export` | `AdminLogsPage.tsx` + `LogDetailModal.tsx` (stack via `dangerouslySetInnerHTML`), Export dropdown incl. XML |
| 3 | C — System & Ops | `/dashboard`, `/health`, `/config/{key}` PUT, `/maintenance/*` | `AdminDashboardPage.tsx`, `AdminOpsPage.tsx` with `ConfigEditor` + `RunSqlConsole` (HTML-rendered result grid) |
| 4 | E — Clinical Overrides | Force-dispense, override, lab-value edit endpoints | `AdminOverridesPage.tsx` — three tabs; no "amended" badge on lab edits |
| 5 | B — Clinical Staff Onboarding | `/doctors/verify-license`, `/staff/onboard` multipart | `AdminOnboardingPage.tsx` — filename rendered via `dangerouslySetInnerHTML` |
| 6 | F — Communications | `/broadcast`, `/messages/{id}/redact` | `AdminBroadcastPage.tsx` + `BroadcastComposer.tsx` with raw-HTML preview |

After each phase: append entries to `VULNERABLE_CONFIG.md` under a new
`## Admin View Redesign — Module X` section, following the existing numbered
vulnerability format (66 was the last entry). Include both backend and UI
vulnerabilities in the same section so the OWASP mapping stays in one place.

---

## 8. Out of Scope

- No real RBAC will be added (`@PreAuthorize`, role checks, server-side role
  verification on the UI). The vulnerable branch is meant to stay vulnerable;
  remediation lives on a separate branch.
- No new DB migrations unless an endpoint genuinely needs new columns
  (e.g., `Doctor.licenseVerified` for Module B may need a V17 migration).
- No automated UI tests for the vulnerable flows — the demos are manual by
  design (instructor walks through the exploit in a browser).

---

## 9. Open Questions

1. Should `POST /users/{id}/impersonate` log the impersonation event? Leaving
   it unlogged is itself an A09 demo, but it removes the trail for instructors
   reviewing the exercise.
2. Should `run-sql` be limited to `SELECT` (still SQLi-demoable) or fully
   open (also enables `DROP TABLE`)? Default to fully open — strongest
   teaching value.
3. Module F broadcast: store HTML in `Message.body` as-is, or add a
   `Message.bodyHtml` column? Reusing `body` keeps the migration burden low.
4. **`AdminPage.tsx` migration** — split into the new `pages/admin/*` files
   in one PR (clean cutover) or keep the old page alive behind a feature
   flag until all new pages exist (safer, but more churn)? Recommendation:
   clean cutover during Phase 0, since the existing page's content is small
   enough to port to `AdminUsersPage` + `AdminLogsPage` in a single commit.
5. **Admin accent colour** — current admin page reuses the patient teal/blue
   palette. Pick a distinct accent (red `#F85149` already used for warning
   states, or amber `#E3B341`) so the admin shell is visually unmistakable.
