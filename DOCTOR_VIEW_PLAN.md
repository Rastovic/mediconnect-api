# Doctor View — Redesign Plan

> Educational OWASP Top 10:2025 project. Every new endpoint below intentionally
> introduces vulnerabilities so they can be demonstrated and exploited. This is
> a **planning document** — nothing here is implemented yet.

---

## 1. Why the Current Doctor View Looks Like a Patient View

The current doctor surface is three thin pieces and none of them feel like a
clinician's tool. `DoctorController.java` exposes only:

| Existing endpoint | Problem |
|---|---|
| `GET  /api/doctors` | Public directory of every doctor — same shape a patient could read. |
| `GET  /api/doctors/profile` | Self-profile read — identical mental model to `GET /api/patients/me`. |
| `PUT  /api/doctors/profile` | Self-profile edit — also identical to the patient profile flow. |

The UI side is worse. `StaffDashboardPage.tsx::DoctorDashboard` (line 130) is
just four count cards plus a list of upcoming appointments, all driven by the
same `GET /api/appointments` and `GET /api/stats/recent` endpoints patients use.
No patient roster, no clinical chart, no notes, no lab ordering, no
e-prescribing flow, no triage, no telemedicine, no AI assist, no referrals,
no handoff. Doctor logs in → sees a patient-style timeline with the word
"Physician" stamped on top.

Missing entirely: assigned patient roster, per-patient chart, clinical notes,
lab/imaging ordering, signed e-prescription, drug-interaction safety check,
telemedicine session, referral inbox, shift handoff, AI-assisted diagnostics.

---

## 2. Goals of the Redesign

1. **Doctor-only surface area** — endpoints a `PATIENT` / `LAB_TECH` /
   `PHARMACIST` / `ADMIN` would never legitimately call (sign a prescription,
   order a lab panel, start a telemedicine session, write a clinical note,
   refer a patient, call the AI summariser, end a shift with a handoff packet).
2. **Clinical workstation feel** — patient roster + drill-down chart + action
   bar (Order Lab / Prescribe / Note / Refer / Start Call), not a dashboard of
   counts.
3. **Each new endpoint demonstrates at least one OWASP Top 10:2025 category** —
   prioritising the three least-covered today: **A02 Security
   Misconfiguration**, **A03 Software Supply Chain Failures**, **A08 Software
   or Data Integrity Failures** (19 occurrences each in `VULNERABLE_CONFIG.md`
   as of session start). Secondary focus: **A09 Logging Failures** (23) and
   **A06 Insecure Design** (35).
4. **Stay consistent with project conventions**: English-only comments, `[A0X]`
   tags inline using the 2025 numbering already in the legend at the top of
   `VULNERABLE_CONFIG.md`, and append a new section to that file after every
   phase ships.

---

## 3. Target Doctor Feature Set

Grouped into **6 modules**. Each module names a primary OWASP category to
demonstrate plus secondary categories that arise naturally.

### Module A — Patient Roster & Chart
The "who am I seeing today" landing surface. Right now the doctor has no list
of *their* patients at all.

| Endpoint | Purpose |
|---|---|
| `GET   /api/doctor/patients` | Roster — patients the doctor has any appointment, prescription, or note for. Filter `?q=`, `?active=`, `?recentDays=N`. |
| `GET   /api/doctor/patients/{id}/chart` | Bundled chart — demographics + active prescriptions + last 5 lab results + last 5 notes + open lab orders. Single call so the chart page does one round trip. |
| `GET   /api/doctor/patients/{id}/timeline` | Chronological feed of every event (appointment, lab, prescription, message, note). |
| `POST  /api/doctor/patients/{id}/star` | Pin/favourite for quick access. |
| `POST  /api/doctor/patients/{id}/handoff` | Generate a shift-handoff token URL another doctor can open to inherit the patient context. |

### Module B — Clinical Notes
Free-text and templated SOAP / progress notes per patient. None of this exists.

| Endpoint | Purpose |
|---|---|
| `POST  /api/doctor/notes` | Create a note. Body: `{ patientId, templateName, data }`. Server renders `templateName` through a template engine. |
| `GET   /api/doctor/notes?patientId=` | List notes for a patient. |
| `GET   /api/doctor/notes/{id}` | Single note with rendered HTML body. |
| `PUT   /api/doctor/notes/{id}` | Edit a note in place — overwrites without versioning. |
| `DELETE /api/doctor/notes/{id}` | Hard delete. |
| `POST  /api/doctor/notes/import` | Multipart `.docx` / `.xml` upload — parses with a `DocumentBuilderFactory` that resolves external entities. |
| `POST  /api/doctor/notes/{id}/co-sign` | Attach a co-signer JWT to a note. |

### Module C — Lab Orders & Imaging
Doctors order tests today by calling the lab tech directly. Move it into the
app.

| Endpoint | Purpose |
|---|---|
| `POST  /api/doctor/lab-orders` | Place a new order. Body includes `panelCode`, `priority`, optional `customQueryUrl` (server fetches reference range from an external lab catalogue). |
| `GET   /api/doctor/lab-orders` | Ordered tests, filter `?status=`, `?patientId=`. |
| `POST  /api/doctor/lab-orders/{id}/sign` | Sign a completed result so it becomes part of the chart. Returns a base64 signature. |
| `POST  /api/doctor/imaging/upload` | Multipart DICOM / PDF upload — filename written verbatim. |
| `POST  /api/doctor/imaging/import-url` | Body: `{ url }` — server fetches the image from `url` and stores it. |
| `GET   /api/doctor/imaging/{id}` | Stream the imaging file back. |

### Module D — E-Prescribing & Drug Safety
A real prescribing flow that also produces a downloadable PDF.

| Endpoint | Purpose |
|---|---|
| `POST  /api/doctor/prescriptions` | Issue a new prescription. Body includes `patientId`, `medication`, `dosage`, optional `pharmacyCallbackUrl` (server POSTs notice to the pharmacy). |
| `POST  /api/doctor/prescriptions/{id}/sign` | Apply the doctor's "wet" signature — stored as MD5 of `(prescriptionId + secret)`. |
| `GET   /api/doctor/prescriptions/{id}/pdf` | Generated PDF with the doctor signature block. PDF is **unsigned** (no PKCS#7 wrapper). |
| `POST  /api/doctor/drug-interactions/check` | Body: `{ medications: [...], catalogueUrl }` — server pulls drug interaction data from `catalogueUrl`. |
| `POST  /api/doctor/prescriptions/{id}/co-sign` | Accepts a second-doctor JWT in the body. |

### Module E — Telemedicine Sessions
Live consult rooms. Pure greenfield — nothing comparable on the patient side.

| Endpoint | Purpose |
|---|---|
| `POST  /api/doctor/sessions` | Start a session. Returns a room URL containing the shared secret in the query string. |
| `GET   /api/doctor/sessions/{id}` | Session detail incl. join URL and signaling endpoint. |
| `POST  /api/doctor/sessions/{id}/end` | Mark ended. |
| `POST  /api/doctor/sessions/{id}/recording` | Body: `{ recordingUrl }` — server downloads the recording from `recordingUrl` and attaches it to the chart. |
| `GET   /api/doctor/sessions/calendar.ics` | iCal feed of the doctor's sessions (no auth — used by external calendar clients). |

### Module F — AI Diagnostics & Referrals
The "cool" surface. AI suggestion + cross-doctor referral inbox, both designed
around supply-chain and integrity weaknesses.

| Endpoint | Purpose |
|---|---|
| `POST  /api/doctor/ai/suggest` | Body: `{ patientId, modelUrl, prompt }` — server forwards the patient's chart to `modelUrl`, returns the response. |
| `POST  /api/doctor/ai/summarize-record` | Body: `{ recordId, modelUrl }` — fetches a `MedicalRecord`, sends to `modelUrl`, stores the response as a new `MedicalRecord` flagged `aiVerified=true`. |
| `GET   /api/doctor/ai/model-info` | Returns the currently configured AI gateway URL **and** API key. |
| `POST  /api/doctor/referrals` | Refer a patient to another doctor. Body includes a serialised Java object (`bundlePayload`, base64-encoded) carrying the chart snapshot. |
| `GET   /api/doctor/referrals/inbox` | Inbox — deserialises `bundlePayload` to render the preview. |
| `POST  /api/doctor/referrals/{id}/accept` | Accept the referral; copies the deserialised chart into the recipient's roster. |

---

## 4. Vulnerabilities per Module

Each endpoint gets at least one OWASP 2025 tag. Goal is to **deepen coverage**
of A02 / A03 / A08, which are tied for least-represented today.

### Module A — Patient Roster & Chart

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `GET /patients` | Returns the entire patient table when called with `?recentDays=0`; raw `LIKE '%' + q + '%'` JPQL concatenation for `?q=` | **A05** + **A06** |
| `GET /patients/{id}/chart` | No "is this my patient" check — any doctor reads any chart | **A01** |
| `GET /patients/{id}/timeline` | Pulls `AuditLog` entries verbatim incl. raw request bodies (passwords, tokens previously POSTed by the patient) | **A09** (logging surface leaks PHI/PII to a different principal) |
| `POST /patients/{id}/star` | "Star" written to a JSON column the server pretty-prints into HTML in the roster sidebar — caller controls the value | **A05** |
| `POST /patients/{id}/handoff` | Returns a handoff URL signed with HMAC-SHA1 and a hardcoded key (`"handoff-secret"`); token has no expiry | **A08** (forgeable handoff URL — integrity loss) |

### Module B — Clinical Notes

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /notes` | `templateName` resolves a file under `notes/templates/${templateName}.ftl` — `../../../etc/passwd.ftl` reads any file the JVM can; rendered through Freemarker with `data` as the model → **SSTI** (`<#assign cmd=...>`) | **A03** (template engine pulled from caller-controlled path) + **A05** |
| `POST /notes/import` (multipart) | `DocumentBuilderFactory.newInstance()` left at defaults → **XXE** (`SYSTEM "file:///etc/passwd"`, OOB DNS exfil) | **A03** |
| `PUT /notes/{id}` | Overwrites in place, no history table, no `editedAt` audit — silent tampering | **A08** + **A09** |
| `DELETE /notes/{id}` | Hard delete, no audit row | **A09** |
| `POST /notes/{id}/co-sign` | Decodes the co-signer JWT with `alg: none` accepted; signature not verified, signer identity taken from `sub` claim verbatim | **A08** + **A07** |

### Module C — Lab Orders & Imaging

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /lab-orders` (`customQueryUrl`) | Server `RestTemplate.getForObject(customQueryUrl, …)` — **SSRF** to `http://169.254.169.254/latest/meta-data/`, internal admin endpoints, file:// scheme enabled | **A03** |
| `POST /lab-orders/{id}/sign` | Signature = `MD5(orderId + ":" + value)` — forgeable + collision-prone; no public-key crypto | **A08** + **A04** |
| `POST /imaging/upload` | Filename written verbatim → path traversal; no MIME / size cap; static-resource handler later serves the file via `/files/**` with `Content-Type` echoed from the upload header | **A02** (misconfigured static serving) + **A03** |
| `POST /imaging/import-url` | Plain SSRF, identical sink to Module C above; also follows `Location:` redirects so `http://attacker/302→file:///` resolves | **A03** |
| `GET /imaging/{id}` | Returns the file with the upload-time `Content-Type` header even for `image/svg+xml` → stored XSS when viewed in the chart | **A05** |

### Module D — E-Prescribing & Drug Safety

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /prescriptions` (`pharmacyCallbackUrl`) | Server fires a POST notice to `pharmacyCallbackUrl` with the prescription payload — **SSRF + data exfil**; no allow-list | **A03** |
| `POST /prescriptions/{id}/sign` | Signature is `MD5(id + secret)`; `secret` is hardcoded in `PrescriptionSigner.java` ("medi-sig-key-2024"); also stored alongside the signature in the response so anyone can replay | **A08** + **A04** |
| `GET /prescriptions/{id}/pdf` | PDF generated with iText, no PKCS#7 signature — forgeable; doctor name field is taken from `User.fullName` and pasted into the PDF with no integrity tag | **A08** |
| `POST /drug-interactions/check` (`catalogueUrl`) | SSRF; also evaluates the JSON response as JavaScript (uses Nashorn `ScriptEngine.eval(json)`) to "normalise" the catalogue → **RCE-equivalent** when the attacker controls the URL | **A03** + **A05** |
| `POST /prescriptions/{id}/co-sign` | Same `alg: none` JWT acceptance as Module B | **A08** + **A07** |

### Module E — Telemedicine Sessions

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /sessions` | Room URL embeds the join secret as `?token=<plaintext>` — leaks into Referer headers, browser history, server access logs | **A02** (Misconfig — secret in URL) + **A09** |
| `GET /sessions/{id}` | Returns the join secret to any authenticated caller; no per-session ACL | **A01** |
| `POST /sessions/{id}/recording` (`recordingUrl`) | SSRF + downloads to a path under `recordings/` named after the URL basename — `?recordingUrl=http://x/..%2f..%2fapp.jar` traverses | **A03** + **A02** |
| `GET /sessions/calendar.ics` | Public (no auth) iCal feed — `SUMMARY:` contains patient name + reason for visit; PHI exposed to anyone with the URL pattern; iCal subscription URL is `/sessions/calendar.ics?doctorId=N` (IDOR by query string) | **A02** + **A01** |

### Module F — AI Diagnostics & Referrals

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `POST /ai/suggest` (`modelUrl`) | Forwards full chart (incl. PHI) to `modelUrl`. SSRF + outbound data exfil; no allow-list; no TLS enforcement | **A03** + **A02** |
| `POST /ai/summarize-record` | The LLM response is written back as a new `MedicalRecord` row with `aiVerified=true` — downstream UI treats `aiVerified` rows as authoritative; integrity entirely client-asserted | **A08** |
| `GET /ai/model-info` | Returns `aiGatewayApiKey` (hardcoded in `application.yaml`) in the JSON response | **A02** (Misconfig — secret exposed) + **A04** |
| `POST /referrals` | `bundlePayload` = base64-encoded `ObjectOutputStream` blob. Server `readObject()`s it on `GET /referrals/inbox` → **Java deserialisation RCE** | **A08** (textbook integrity failure) |
| `GET /referrals/inbox` | Deserialises **every** unread referral on page load, no class allow-list | **A08** |
| `POST /referrals/{id}/accept` | Copies the deserialised `bundlePayload` into the recipient's chart, retaining attacker-controlled `MedicalRecord.id` (mass-assignment) | **A08** + **A06** |

### Summary — Coverage Matrix

| OWASP 2025 | Existing repo count | New endpoints added by this plan |
|---|---|---|
| A01 Broken Access Control | 101 | 3 |
| A02 Security Misconfiguration | 19 | **6** |
| A03 Software Supply Chain Failures | 19 | **8** |
| A04 Cryptographic Failures | 48 | 3 |
| A05 Injection | 42 | 4 |
| A06 Insecure Design | 35 | 2 |
| A07 Authentication Failures | 46 | 2 |
| A08 Software or Data Integrity Failures | 19 | **9** |
| A09 Logging Failures | 23 | 4 |
| A10 Mishandling Exceptional Conditions | 36 | 0 |

This is the main argument for the redesign — the three least-covered
categories (A02 / A03 / A08, all at 19) each get a fresh 6-9 demos, lifting
them to first-tier coverage without piling more material onto the already
crowded A01 / A04 / A07 surfaces.

---

## 5. UI Changes

The UI lives in a separate repo at
`/Users/jelenarastovic/Downloads/mediconnect-frontend`. Stack confirmed:
React 18 + Vite + TypeScript + Tailwind, react-router-dom v7, TanStack Query,
axios (`src/api/axiosInstance.ts`), Radix-derived `components/ui/*`, Lucide
icons, Recharts. Existing admin redesign shipped `components/admin/AdminLayout
+ AdminSidebar` — the doctor view mirrors that pattern.

### 5.1 Why the Current Doctor Page Feels Like a Patient Page

Three concrete reasons, all visible in the existing code:

1. **`StaffDashboardPage.tsx` is one file shared with LAB_TECH and PHARMACIST.**
   `DoctorDashboard` (line 130) is a 110-line subcomponent — counts and a list.
   Same `AppLayout`, same patient sidebar, no doctor-specific shell.
2. **Doctor inherits the patient `Sidebar.tsx`** (Dashboard, Appointments,
   Medical Records, Lab Results, Messages, Prescriptions, Refills, Profile).
   The admin redesign already filters this for `role === 'ADMIN'` (line 75) —
   doctors still see the full patient nav.
3. **No drill-down.** A doctor seeing "12 upcoming appointments" cannot click
   into a patient and see anything that looks like a chart. They jump to the
   shared `/appointments` table — identical to the patient view.

### 5.2 Screen Inventory

Replace the `DoctorDashboard` subcomponent with a top-level **Doctor Console**
that has its own sidebar. Each section maps 1:1 to a backend module from §3.

| Section | Route | Page component | Backend module |
|---|---|---|---|
| **Dashboard** | `/doctor` | `pages/doctor/DoctorDashboardPage.tsx` | A (summary) |
| **My Patients** | `/doctor/patients`, `/doctor/patients/:id` | `DoctorPatientsPage.tsx`, `DoctorPatientChartPage.tsx` | A |
| **Notes** | `/doctor/notes`, `/doctor/notes/:id` | `DoctorNotesPage.tsx`, `DoctorNoteDetailPage.tsx` | B |
| **Lab & Imaging** | `/doctor/labs` | `DoctorLabsPage.tsx` (sub-tabs: Orders / Imaging) | C |
| **Prescribe** | `/doctor/prescribe` | `DoctorPrescribePage.tsx` | D |
| **Telemedicine** | `/doctor/telemedicine`, `/doctor/telemedicine/:id` | `DoctorSessionsPage.tsx`, `DoctorSessionRoomPage.tsx` | E |
| **AI Assist** | `/doctor/ai` | `DoctorAIAssistantPage.tsx` | F |
| **Referrals** | `/doctor/referrals` | `DoctorReferralsPage.tsx` | F |

The `DoctorDashboard` block in `StaffDashboardPage.tsx` (lines 130–248) is
removed. The shared file keeps `LabTechDashboard` and `PharmacistDashboard`
and the role switcher at the bottom — only the doctor branch moves out.

### 5.3 Layout & Routing Changes

| File | Change |
|---|---|
| `src/components/layout/Sidebar.tsx` line 75 | Extend the `resolvedNavItems` filter: when `user?.role === 'DOCTOR'` hide every patient item exactly like the existing admin branch already does. |
| **New** `src/components/doctor/DoctorLayout.tsx` | Mirrors `AdminLayout.tsx` — wraps `DoctorSidebar` + content. Accent colour **green-amber** (`#3FB950` / `#E3B341`) to distinguish from the admin red shell. |
| **New** `src/components/doctor/DoctorSidebar.tsx` | Dashboard, My Patients, Notes, Lab & Imaging, Prescribe, Telemedicine, AI Assist, Referrals. |
| **New** `src/auth/DoctorRoute.tsx` | Mirrors `AdminRoute.tsx`. **Deliberately omits `requiredRole`** — same A01 demo pattern as the admin redesign. |
| `src/App.tsx` | Add `/doctor/*` routes under `<DoctorRoute>`. Keep the existing `/staff` route alive (compat — LAB_TECH and PHARMACIST still use it). Update `Sidebar.tsx`'s `dashboardPath` (line 66) so `DOCTOR` resolves to `/doctor` instead of `/staff`. |

### 5.4 New Components

Reuse `components/ui/*` primitives wherever possible. New pieces:

- `pages/doctor/*` — eight page components (see 5.2).
- `components/doctor/DoctorLayout.tsx` + `DoctorSidebar.tsx`.
- `components/doctor/PatientChartHeader.tsx` — demographics strip; renders
  `aiVerified` MedicalRecord rows with a green check (no provenance shown).
- `components/doctor/NoteEditor.tsx` — template picker + Markdown textarea
  + Preview pane (rendered via `dangerouslySetInnerHTML`).
- `components/doctor/LabOrderForm.tsx` — `customQueryUrl` field visible in UI
  so the SSRF demo is one click.
- `components/doctor/PrescriptionSigner.tsx` — Sign button that calls
  `/prescriptions/{id}/sign` and shows the returned MD5 in plain text.
- `components/doctor/DrugInteractionPanel.tsx` — `catalogueUrl` field exposed.
- `components/doctor/TelemedicineRoom.tsx` — embeds an `<iframe>` with the
  room URL (including `?token=…` in the src), so the secret leaks via
  `Referer` to any third-party asset the iframe loads.
- `components/doctor/AIAssistantPanel.tsx` — `modelUrl` text input + Send;
  response rendered with `dangerouslySetInnerHTML`.
- `components/doctor/ReferralBundleViewer.tsx` — preview of an inbox bundle,
  renders the `summary` field with `dangerouslySetInnerHTML`.
- `api/doctor.ts` — typed client wrappers, mirrors `api/admin.ts`.

### 5.5 UI-Layer Vulnerabilities

**Existing UI vulnerabilities — lean on, don't duplicate:**

| Location | Vulnerability | OWASP |
|---|---|---|
| `auth/AuthContext.tsx` | JWT + user in `localStorage`; role decoded client-side without signature check | **A07** |
| `auth/ProtectedRoute.tsx` | Supports `requiredRole`, but existing staff/admin routes deliberately omit it | **A01** |
| `pages/MessagesPage.tsx:293` | Message content via `dangerouslySetInnerHTML` — already a recipient-side XSS sink | **A05** |
| `pages/StaffDashboardPage.tsx:171` | Role-based banner is client-side only | **A01** |

**New UI vulnerabilities added by this plan:**

| UI surface | Vulnerability | OWASP |
|---|---|---|
| `NoteEditor` preview pane | `dangerouslySetInnerHTML` of the rendered template — SSTI payloads from §4 surface as live `<script>` in the doctor's session | **A05** + **A03** |
| `PatientChartHeader` "AI verified" check | Green check renders whenever `record.aiVerified === true`, no provenance / signature shown to the clinician → integrity loss is invisible | **A08** |
| `LabOrderForm` `customQueryUrl` input | Exposed by default; placeholder is `https://lab-catalogue.internal/...` so the SSRF demo is obvious | **A03** |
| `TelemedicineRoom` iframe | `src` contains `?token=<plaintext>` — leaks through `Referer` to any image / font the embedded page fetches | **A02** |
| `DrugInteractionPanel` response render | Result table uses `dangerouslySetInnerHTML` on each cell so HTML coming from the `catalogueUrl` runs in the doctor's session | **A03** + **A05** |
| `AIAssistantPanel` model picker | Free-form `modelUrl` text input, no allow-list, no warning copy. Submit button labelled "Send chart to model" — the data-exfil is the feature. | **A03** |
| `ReferralBundleViewer` | Renders inbox `summary` HTML directly; bundle accepted into chart with one click, no diff / no provenance | **A08** |
| `DoctorSidebar` "AI Assist" badge | Pulled from `GET /ai/model-info` — UI renders the response JSON including the API key in a tooltip | **A02** |
| Global CSP / `X-Frame-Options` | `vite.config.ts` ships no CSP. Doctor console embeds an `<iframe>` for telemedicine → clickjacking on Sign Prescription button. | **A02** |

### 5.6 UX Signals to Keep the Demos Teachable

Two rules, same as the admin redesign:

1. **No silent failures.** Every privileged action (Sign Prescription,
   AI Suggest, Import URL, Accept Referral) shows a `useToast()` line naming
   the OWASP tag it just exercised — same convention as `AdminPage.tsx`.
2. **Visible markers.** AI-verified records get a `[A08]` ribbon in the chart.
   Lab orders with a `customQueryUrl` set get a `[A03]` ribbon. Sessions
   started with a plaintext token get a `[A02]` ribbon. Without the ribbons
   the bug categories blur into "stuff that worked".

### 5.7 Cross-Repo Dependencies

Three places where backend changes ripple into the UI (or vice versa) and
need coordinated edits:

1. **`Sidebar.tsx` `dashboardPath` mapping (line 66).** Today
   `role === 'DOCTOR'` resolves to `/staff`. Phase 0 must update it to
   `/doctor` while leaving `/staff` alive for LAB_TECH / PHARMACIST. If only
   one side ships, doctors either still land on the old shared page or land
   on a missing route.
2. **`GET /api/appointments` shape stays unchanged.** The new doctor chart
   page calls it via `DoctorPatientsPage` *and* the existing
   `AppointmentsPage` still consumes it. Adding required fields to the DTO
   would break the patient view. New chart-only fields go on `/doctor/...`
   endpoints.
3. **`/staff` still exists for LAB_TECH and PHARMACIST.** Do not delete
   `StaffDashboardPage.tsx`. Only `DoctorDashboard` (lines 130–248) and the
   `user?.role === 'DOCTOR'` branch at line 512 are removed.

---

## 6. File Layout Changes

### 6.1 Backend (`mediconnect-api`)

```
controller/
  DoctorController.java             ← KEEP (extend /profile endpoints stay)
  DoctorRosterController.java       ← NEW (Module A)
  DoctorNoteController.java         ← NEW (Module B)
  DoctorLabController.java          ← NEW (Module C)
  DoctorPrescribingController.java  ← NEW (Module D)
  DoctorSessionController.java      ← NEW (Module E)
  DoctorAIController.java           ← NEW (Module F — AI half)
  DoctorReferralController.java     ← NEW (Module F — referral half)

service/
  DoctorRosterService.java
  DoctorNoteService.java
  DoctorLabService.java
  DoctorPrescribingService.java
  DoctorSessionService.java
  DoctorAIService.java
  DoctorReferralService.java
  PrescriptionSigner.java           ← NEW (MD5 signer for [A08])
  HandoffTokenIssuer.java           ← NEW (HMAC-SHA1 with hardcoded key)
  ExternalCatalogueClient.java      ← NEW (RestTemplate sink for the [A03] SSRFs)
  TemplateRenderer.java             ← NEW (Freemarker, no sandbox)

entity/
  ClinicalNote.java                 ← NEW
  LabOrder.java                     ← NEW (separate from LabResult)
  ImagingFile.java                  ← NEW
  TelemedicineSession.java          ← NEW
  Referral.java                     ← NEW

dto/
  PatientChartDto.java
  ClinicalNoteDto.java
  LabOrderDto.java
  ImagingFileDto.java
  PrescriptionSignatureDto.java
  TelemedicineSessionDto.java
  AISuggestRequestDto.java
  ReferralDto.java

db/migration/
  V18__clinical_notes.sql           ← Phase 2 (Module B)
  V19__lab_orders_imaging.sql       ← Phase 3 (Module C)
  V20__telemedicine_sessions.sql    ← Phase 5 (Module E)
  V21__referrals.sql                ← Phase 6 (Module F)
  V22__doctor_seed.sql              ← Phase 6 tail — seeds two doctors +
                                       notes / orders / sessions / referrals
                                       so demos work out of the box

security/
  (no @PreAuthorize anywhere — keeps the A01 surface aligned with
   SecurityConfig permitAll())
```

`SecurityConfig.java` keeps `/api/doctor/**` on `permitAll()` (existing A01).
No change there.

### 6.2 Frontend (`mediconnect-frontend`)

```
src/
  pages/
    StaffDashboardPage.tsx          ← edit: drop DoctorDashboard branch,
                                       keep LabTech + Pharmacist
    doctor/                         ← NEW directory
      DoctorDashboardPage.tsx
      DoctorPatientsPage.tsx
      DoctorPatientChartPage.tsx
      DoctorNotesPage.tsx
      DoctorNoteDetailPage.tsx
      DoctorLabsPage.tsx
      DoctorPrescribePage.tsx
      DoctorSessionsPage.tsx
      DoctorSessionRoomPage.tsx
      DoctorAIAssistantPage.tsx
      DoctorReferralsPage.tsx

  components/
    layout/
      Sidebar.tsx                   ← extend filter for DOCTOR
    doctor/                         ← NEW directory
      DoctorLayout.tsx
      DoctorSidebar.tsx
      PatientChartHeader.tsx
      NoteEditor.tsx
      LabOrderForm.tsx
      PrescriptionSigner.tsx
      DrugInteractionPanel.tsx
      TelemedicineRoom.tsx
      AIAssistantPanel.tsx
      ReferralBundleViewer.tsx

  auth/
    DoctorRoute.tsx                 ← NEW (mirrors AdminRoute.tsx; no requiredRole)

  api/
    doctor.ts                       ← NEW (typed wrappers for /api/doctor/**)
```

Shared `components/ui/*` primitives are reused as-is — no design-system work.

---

## 7. Implementation Phases

Each phase is independently demoable, ships **backend endpoints + matching UI
screens together**, and leaves both repos runnable. **After every phase, run
the bug-check pass in §7.1 before moving on.**

| Phase | Module | Backend deliverable | UI deliverable |
|---|---|---|---|
| 0 | Shell | Empty controllers + routes wired to `permitAll()`; no schema yet (migrations split per phase — see §9.5) | `Sidebar.tsx` filter extended for DOCTOR; `DoctorRoute.tsx`, `DoctorLayout.tsx`, `DoctorSidebar.tsx`; `App.tsx` adds `/doctor/*`; `dashboardPath` updated; `DoctorDashboard` branch removed from `StaffDashboardPage.tsx` (LAB_TECH + PHARMACIST stay) |
| 1 | A — Roster & Chart | `/api/doctor/patients`, `/patients/{id}/chart`, `/timeline`, `/star`, `/handoff` (no migration — reuses existing tables) | `DoctorDashboardPage.tsx`, `DoctorPatientsPage.tsx`, `DoctorPatientChartPage.tsx`, `PatientChartHeader` |
| 2 | B — Clinical Notes | `V18__clinical_notes.sql`; `/api/doctor/notes/*` incl. multipart `import` | `DoctorNotesPage.tsx`, `DoctorNoteDetailPage.tsx`, `NoteEditor` with raw-HTML preview |
| 3 | C — Lab & Imaging | `V19__lab_orders_imaging.sql`; `/lab-orders`, `/lab-orders/{id}/sign`, `/imaging/upload`, `/imaging/import-url`, `/imaging/{id}` | `DoctorLabsPage.tsx` (Orders / Imaging tabs), `LabOrderForm` with `customQueryUrl` field |
| 4 | D — E-Prescribing | `/api/doctor/prescriptions`, `/sign`, `/pdf`, `/drug-interactions/check`, `/co-sign` (extends existing `prescriptions` table — adds `signature_md5` column via small migration if needed) | `DoctorPrescribePage.tsx`, `PrescriptionSigner`, `DrugInteractionPanel` |
| 5 | E — Telemedicine | `V20__telemedicine_sessions.sql`; `/api/doctor/sessions/*`, `/calendar.ics` | `DoctorSessionsPage.tsx`, `DoctorSessionRoomPage.tsx`, `TelemedicineRoom` iframe |
| 6 | F — AI & Referrals | `V21__referrals.sql`; `/api/doctor/ai/*`, `/api/doctor/referrals/*` | `DoctorAIAssistantPage.tsx`, `DoctorReferralsPage.tsx`, `ReferralBundleViewer` |

### 7.1 Per-Phase Bug-Check Pass

After **every** phase, before opening the next:

1. **Backend** — `./mvnw -q -DskipITs=false test` ; spin the app
   (`./mvnw spring-boot:run`) and hit every new endpoint with curl using the
   seeded `doctor1` / `doctor2` accounts in `TEST_ACCOUNTS.md`. 200 path +
   one negative path per endpoint.
2. **Frontend** — `npm run -s typecheck` and `npm run -s lint`; `npm run dev`
   and click through each new screen. Check the browser network tab — every
   call should target an endpoint that exists in the phase just shipped (no
   404s, no CORS errors, no shape mismatches).
3. **Cross-repo compat** — exercise the legacy patient sidebar as a
   `PATIENT` user, the LAB_TECH / PHARMACIST staff dashboard as those roles,
   and the admin console as `admin`. None of these should regress — the
   doctor work must not steal nav items, axios baseURL, or token storage
   keys from the other roles.
4. **Doc update** — append a `## Doctor View Redesign — Module X` section
   to `VULNERABLE_CONFIG.md`, continuing the existing numbered vulnerability
   format. Include both backend and UI entries in the same section.
5. **Fix on the same branch.** Anything broken found in 1-3 is patched before
   tagging the phase done — no carrying a known regression into the next
   phase.

### 7.2 Compatibility Guards (apply to every phase)

- DTO shape changes to **existing** entities (`Prescription`, `LabResult`,
  `MedicalRecord`, `Appointment`) are forbidden in this redesign. New fields
  live on new entities (`ClinicalNote`, `LabOrder`, `ImagingFile`,
  `TelemedicineSession`, `Referral`).
- `GET /api/appointments` and `GET /api/prescriptions` responses must stay
  byte-identical so `StaffDashboardPage.tsx` (LAB_TECH / PHARMACIST) and the
  patient views keep working.
- `SecurityConfig` adds `/api/doctor/**` to `permitAll()` — the existing
  `/api/admin/**` rule must not be touched.
- The frontend `axiosInstance.ts` baseURL and token-injection interceptor
  are not modified. New endpoints reuse the same axios instance.

---

## 8. Out of Scope

- No real RBAC will be added (`@PreAuthorize`, role checks, server-side role
  verification on the UI). The vulnerable branch is meant to stay vulnerable;
  remediation lives on a separate branch.
- No new tests for the vulnerable flows — the demos are manual by design
  (instructor walks through the exploit in a browser / curl).
- No production-grade telemedicine signaling. The Module E "room" is a fake
  page that displays the connection params — the vulns are in the issuance,
  not in the media stream.
- No real LLM call for Module F. `modelUrl` is the SSRF sink; pointing it at
  a netcat listener is enough to demonstrate exfil.
- No production-grade DICOM parser. Imaging is treated as opaque bytes.

---

## 9. Decisions (defaults applied)

All five open questions resolved to defaults. Recorded here so implementation
phases proceed without re-litigating.

1. **Java deserialisation for referrals — YES.** Module F's `bundlePayload`
   uses raw `ObjectInputStream.readObject()` on the base64-decoded blob. No
   class allow-list, no `ObjectInputFilter`. Strongest A08 demo in the plan.
   Branch already runs `permitAll()` — blast radius stays local-dev.
2. **AI gateway URL allow-list — NONE.** `modelUrl` accepts any URL including
   `http://`, `file://`, `http://169.254.169.254/...`. Risk noted in the new
   `VULNERABLE_CONFIG.md` section shipped with Phase 6. Strongest SSRF + PHI
   exfil demo wins over guardrails.
3. **Telemedicine room — static page.** `DoctorSessionRoomPage.tsx` renders
   join params in a `<pre>` block + an `<iframe>` whose `src` carries
   `?token=<plaintext>`. No real WebRTC. The A02 demo is the token in the
   URL, not the media stream.
4. **Doctor accent — `#3FB950` (green) primary, `#E3B341` (amber) secondary.**
   `DoctorLayout.tsx` border + `DoctorSidebar.tsx` active item use green;
   ribbons / `[A0X]` chips use amber. Distinct from patient teal/blue
   (`#2F81F7`) and admin red (`#F85149`).
5. **Migration ordering — SPLIT per module.** `V18` clinical_notes (Phase 2),
   `V19` lab_orders + imaging_files (Phase 3), `V20` telemedicine_sessions
   (Phase 5), `V21` referrals (Phase 6). Phase 0 ships only the empty
   controllers + routing — no schema yet. Each phase independently revertable
   by dropping its own tables.
