# Doctor Appointments Tab — Implementation Plan

> Educational OWASP Top 10:2025 project. Replaces the existing **AI Assist** tab
> (Module F AI half) with a real clinical workflow page. Companion plan to
> `DOCTOR_VIEW_PLAN.md`. Nothing here is implemented yet.

---

## 1. Motivation

The Doctor Console ships eight tabs after the 6-phase redesign. Two of them
feel weak:

1. **AI Assist** (`/doctor/ai`) is a thin SSRF + key-leak demo with two
   buttons and a model picker. The OWASP coverage it adds (A02 #247, A03
   #249, A08 #250) is already redundant with Module D's drug-interaction
   panel (A03 SSRF + Nashorn eval) and Module C's catalogue SSRF.
2. **No appointment view at all** — a clinical workstation without an
   appointments inbox is missing the doctor's most-used workflow.

This plan removes AI Assist and replaces the slot with **Appointments**.
The new tab carries genuine doctor functionality (today's schedule, accept /
decline / reschedule, no-show, complete with notes, conflict detection) and
loads the OWASP categories that are now *under*-represented after Phase 6.

---

## 2. AI Assist Removal — Scope

### 2.1 Backend files to delete

```
controller/DoctorAIController.java          ← delete
service/DoctorAIService.java                ← delete
```

The `MedicalRecord.aiVerified` + `aiModelUrl` columns (V24 migration) stay
on disk — they are still used by **Module F Referrals** to flag bundles
imported into a chart, and removing them would force a V25 down-migration.
Existing rows with `aiVerified = true` stay; the producing endpoint is gone.

### 2.2 Frontend files to delete

```
src/pages/doctor/DoctorAIAssistantPage.tsx     ← delete
src/components/doctor/AIAssistantPanel.tsx     ← delete (exports both
                                                  AIAssistantPanel and
                                                  AIModelInfoCard)
```

### 2.3 Edits

| File | Change |
|---|---|
| `src/api/doctor.ts` | Remove `aiModelInfo`, `aiSuggest`, `aiSummarizeRecord` wrappers + `AIModelInfo`, `AISuggestRequest`, `AISuggestResponse`, `AISummarizeRequest` types |
| `src/App.tsx` | Remove `DoctorAIAssistantPage` import + `/doctor/ai` route |
| `src/components/doctor/DoctorSidebar.tsx` | Replace `AI Assist` nav item with `Appointments` (icon `Calendar`, route `/doctor/appointments`, OWASP tag `A06`) |

### 2.4 `VULNERABLE_CONFIG.md` cleanup

Entries **#247, #248, #249, #250, #251, #255** describe the now-deleted AI
sinks. Two options:

- **Option A** — strike-through the entries with a note "REMOVED in
  Appointments redesign — see entries #256+".
- **Option B** — physically remove the entries and shift numbering down.

Recommendation: **Option A**. Numbering stability matters because the doc
is cross-referenced by other entries (`#249` is named in `#255`).
Strike-through keeps history readable for instructors comparing old vs new.

---

## 3. Appointments Tab — Goals

1. **Real doctor workflow** — accept, decline, reschedule, mark complete /
   no-show, attach a clinical note on completion.
2. **Today + week + month** views with conflict highlighting.
3. **At least one OWASP demo per endpoint**, biased toward the categories
   still on the lighter side of coverage after Phase 6:
   - **A06 Insecure Design** (35 → bump) — no double-book check, no
     capacity cap, no cooling-off between cancels.
   - **A07 Authentication Failures** (46 → bump) — `actorDoctorId` from
     body again, no JWT correlation.
   - **A09 Logging Failures** (30 → bump) — bulk operations log one audit
     row for an arbitrary number of mutations.
4. **One "fun" advanced demo** — CSV import + export with formula
   injection on export (Excel runs `=cmd|...`) + unbounded row count on
   import (DoS-class).

---

## 4. Endpoint Surface

Reuses existing `/api/appointments` for low-level CRUD (don't fragment the
patient-side flow); adds `/api/doctor/appointments/*` for doctor-specific
batch + workflow actions.

| Method | Path | Purpose |
|---|---|---|
| `GET`   | `/api/doctor/appointments` | Doctor-scoped list. Query: `?doctorId=`, `?from=`, `?to=`, `?status=`, `?q=` |
| `GET`   | `/api/doctor/appointments/today` | Convenience — today's schedule for `?doctorId=` |
| `GET`   | `/api/doctor/appointments/conflicts` | Returns pairs that overlap on the same `doctorId` |
| `POST`  | `/api/doctor/appointments/{id}/approve` | Patient request → APPROVED. Body: `{actorDoctorId, slotDurationMin}` |
| `POST`  | `/api/doctor/appointments/{id}/decline` | → CANCELLED with `declineReason` (stored as note) |
| `POST`  | `/api/doctor/appointments/{id}/reschedule` | Body: `{newScheduledAt, reason}` — overwrites in place |
| `POST`  | `/api/doctor/appointments/{id}/complete` | → COMPLETED + writes a `MedicalRecord` from `body.notes` |
| `POST`  | `/api/doctor/appointments/{id}/no-show` | → CANCELLED with subtype tracked in notes |
| `POST`  | `/api/doctor/appointments/bulk-status` | Body: `{ids:[...], status:"APPROVED"}` — fan-out |
| `POST`  | `/api/doctor/appointments/import` | Multipart CSV upload, unbounded |
| `GET`   | `/api/doctor/appointments/export.csv` | CSV export with formula-injection risk on `notes` / `patientName` |

---

## 5. Vulnerabilities per Endpoint

| Endpoint | Vulnerability | OWASP |
|---|---|---|
| `GET /doctor/appointments` (`?q=`) | Raw concat into JPQL (`LIKE '%' + q + '%'`) — same pattern as Module A roster | **A05** |
| `GET /today` | `?doctorId=` IDOR — any doctor enumerates any doctor's day | **A01** |
| `GET /conflicts` | Returns full row pairs incl. patient PII for *every* doctor when called without filter | **A01** + **A06** |
| `POST /{id}/approve` | No double-book check; no `slotDurationMin` cap; same patient can be approved by two doctors at the same minute | **A06** (Insecure Design — no clinical safety rule) |
| `POST /{id}/approve` | `actorDoctorId` from body — caller writes any doctor's name as the approver | **A07** |
| `POST /{id}/decline` | `declineReason` stored verbatim, rendered with `dangerouslySetInnerHTML` in the inbox feed | **A05** |
| `POST /{id}/reschedule` | Overwrites `scheduledAt` + `notes` in place; no revision table; no original time preserved | **A08** (Integrity) |
| `POST /{id}/complete` | Auto-creates `MedicalRecord` with `notes` as `diagnosis` field; no signature, no countersignature, no audit row for the chart row creation | **A08** + **A09** |
| `POST /{id}/no-show` | No rate limit — any caller can mark every appointment no-show in one loop | **A06** |
| `POST /bulk-status` | `ids` list unbounded; single audit row covers N mutations | **A09** + **A06** |
| `POST /import` (multipart CSV) | No row cap; large file OOMs the JVM | **A06** (DoS-class) |
| `POST /import` | CSV parsed naively — `notes` column accepts `=HYPERLINK("http://attacker",A1)` which fires when admin opens the export later | **A03** (CSV / formula injection — supply-chain via spreadsheet) |
| `GET /export.csv` | Cell values written verbatim; if a `notes` field starts with `=`, `@`, `+`, `-`, the cell becomes an Excel formula on open — RCE-class via `=cmd\|'/c calc'!A1` on Windows | **A03** |
| `GET /export.csv` | No `Content-Disposition: attachment` constraint — opens inline in the browser, leaking the dataset into the browser cache | **A02** |
| Frontend list table | `appointment.notes` rendered via `dangerouslySetInnerHTML` so the decline-reason stored XSS reaches every doctor opening their inbox | **A05** |

**Net coverage shift**: −6 entries on AI surface (removed) + ~12 new
entries here. A03 (+2) gains CSV demos. A06 (+4) catches up. A07 (+1)
edges up. A09 (+2). A08 (+2). A05 (+2).

---

## 6. UI Changes

### 6.1 Routing + nav

| File | Change |
|---|---|
| `src/App.tsx` | Drop `/doctor/ai`; add `/doctor/appointments` + `/doctor/appointments/conflicts` |
| `src/components/doctor/DoctorSidebar.tsx` | Replace `Sparkles → AI Assist` nav item with `Calendar → Appointments` (OWASP tag `A06`) |

### 6.2 Pages + components

```
src/pages/doctor/
  DoctorAppointmentsPage.tsx              ← NEW
  DoctorAppointmentConflictsPage.tsx      ← NEW (sub-route)

src/components/doctor/
  AppointmentList.tsx                     ← NEW (table w/ filter bar)
  AppointmentRowActions.tsx               ← NEW (approve/decline/reschedule/complete/no-show)
  AppointmentImportPanel.tsx              ← NEW (CSV picker + import-result toast)
  AppointmentExportButton.tsx             ← NEW (downloads /export.csv)
  RescheduleDialog.tsx                    ← NEW
  CompleteAppointmentDialog.tsx           ← NEW (notes textarea, creates MedicalRecord on submit)
  ConflictBadge.tsx                       ← NEW (red badge when row overlaps another)
```

### 6.3 Page layout

```
DoctorAppointmentsPage
├── PageHeader (title, count, Import / Export buttons)
├── [A06] banner explaining the absent double-book check
├── Filter bar (doctorId, from, to, status, free-text q)
├── Tabs: Today | Upcoming | Pending | Past | Conflicts
├── AppointmentList
│     - notes column renders with dangerouslySetInnerHTML  [A05]
│     - conflict column highlights overlaps               [A06]
└── AppointmentImportPanel + AppointmentExportButton
```

`AppointmentRowActions` exposes a compact button row (Approve / Decline /
Reschedule / Complete / No-show) — each one fires a toast naming the
OWASP tag it just exercised, same convention as Modules A–F.

---

## 7. Implementation Phases

| Phase | Scope | Deliverable |
|---|---|---|
| **A** | AI Assist deletion | Backend files removed, frontend page+component removed, sidebar swapped, App.tsx route removed, api/doctor.ts trimmed, VULNERABLE_CONFIG.md entries struck-through |
| **B** | Appointments backend | `DoctorAppointmentController` + `DoctorAppointmentService` + DTOs (`AppointmentRowDto`, `ConflictPairDto`, `CompleteAppointmentRequest`, `BulkStatusRequest`); migration `V25__appointments_actor_columns.sql` if `actor_doctor_id` / `decline_reason` need to live on the existing `appointments` row (alternative: store in `notes` to avoid schema churn — pick before implementing) |
| **C** | Appointments frontend | New pages + components from §6.2 |
| **D** | Bug-check + VULNERABLE_CONFIG.md append | Per `DOCTOR_VIEW_PLAN.md` §7.1 protocol — mvn test, curl every new endpoint, tsc + eslint, cross-role smoke, append `## Appointments Tab — Module G` section with new vulnerability entries |

**Each phase ends with the per-phase bug-check pass** (backend boot,
endpoint smoke, frontend tsc + eslint, regression check across the other
7 doctor tabs + admin / staff consoles).

---

## 8. Compatibility Guards

- `GET /api/appointments` (existing patient endpoint) **must stay byte-identical**. `StaffDashboardPage.tsx` (LAB_TECH + PHARMACIST) no longer renders appointments after the Phase 0 split, but the patient dashboard still consumes the old shape. Don't add required fields to `AppointmentDto`.
- `Appointment` entity additions are nullable + default-valued so the V25 (or §B alternative) migration is forward-only with no rewrite of existing rows.
- The `AI Assist` slot in `DoctorSidebar.tsx` is removed cleanly — no leftover route + no leftover import. `tsc -b --noEmit` must pass with zero unused-import warnings.
- `MedicalRecord.aiVerified` + `aiModelUrl` columns stay on the schema (Referrals module still writes them). Frontend chart's `aiVerified` ribbon stays in place for referral-imported rows.

---

## 9. Open Questions

1. **Schema vs. notes-only.** Should `actor_doctor_id` + `decline_reason` get their own columns on `appointments` (cleaner but adds V25), or stuff them into the existing `notes` field as a tagged blob `[approved-by:N|declined:reason...]`? **Default: V25 migration** — clean schema is worth one more migration; the embed-in-notes pattern is the bug that downstream consumers regret.
2. **Bulk import row cap.** Strict cap (1000 rows) is safe but kills the A06 DoS demo. **Default: no cap on the unsafe branch** (matches Module A `bulk-delete` design from admin redesign).
3. **CSV formula injection on export.** Strip leading `=`/`+`/`-`/`@`? **Default: do not strip** — that's the demo.
4. **Phase A first, then B–D?** Or one merged PR? **Default: split** — deletion is reversible only via VCS; small isolated diff easier to revert if a stakeholder objects to dropping AI Assist.
5. **Should /api/appointments existing endpoints get any new vulns?** **Default: no.** Keep new attack surface on `/api/doctor/appointments/*` so the patient flow stays untouched.

---

## 10. Out of Scope

- No actual scheduling engine (free-slot search, recurrence rules, time-zone normalisation). The demo treats `scheduledAt` as a flat `LocalDateTime`.
- No real CSV parser library — keep it to `String.split(",")` so the formula-injection + unbounded-row demos are self-contained.
- No real iCal feed at this surface — Module E already covers it for telemedicine sessions.
- No SMS / email reminder hook — outside the OWASP teaching scope.
