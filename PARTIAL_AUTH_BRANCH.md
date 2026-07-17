# partial-auth branch — evaluation testbed (middle corner)

Derived from `fixed` (commit c3449ac). This branch exists solely as the middle
corner of the three-branch evaluation for the differential access-control tool.
It is NOT a product branch and must never be merged into `fixed` or `master`.

## What it models

Function-level authorization is PRESENT (role gates, authentication required).
Object-level ownership authorization is ABSENT. This is the most common
real-world Broken Access Control pattern (OWASP A01 / CWE-639): a request passes
the coarse role check, then reads or mutates an object it does not own because no
per-object ownership check runs.

The transformation vs `fixed`: every `@PreAuthorize("@authz.<objectCheck>(...)")`
predicate was replaced with a coarse role/authentication gate. Pure function-level
gates on `fixed` (for example `hasRole('ADMIN')`, dispense, create, search) were
left untouched. `AuthzService` is retained but no longer referenced by controllers.

## The three corners

| Branch | SecurityConfig | Object-level ownership | Tool should find |
| --- | --- | --- | --- |
| `ctf-platform` (no-auth) | `permitAll()` everywhere, JWT filter swallows | none, no authn | every access is a breach (trivial recall) |
| `partial-auth` (this) | authn + role gates | none | breaches ONLY among role-permitted 200s (the real test of ownership inference) |
| `fixed` (correct-auth) | authn + role + `@authz` object checks | full | nothing (precision / silence) |

## Why the middle corner is the discriminative test

On `ctf-platform` everything returns 200, so a dumb "did I get data" scanner
scores full recall; ownership inference is not exercised. On `fixed` breaches
return 403, so there is nothing to flag. Only here does a 200 to a non-owning
caller sit next to a legitimate 200 to the owning caller, forcing the tool to
attribute each object to its owner and decide breach vs legitimate. That
decision is the tool's contribution.

Both `patient1` and `patient2` own distinct appointments, lab results, medical
records, and prescriptions (seed V11/V12), so genuine breach cases and genuine
legitimate cases both exist.

## Per-endpoint ground truth

Object-level ownership REMOVED here (breach-capable — cross-identity 200 that
`fixed` returns 403 for). CWE-639.

- `GET /api/appointments/{id}`, `PUT /api/appointments/{id}`, `POST /api/appointments/{id}/cancel` (role gate kept: PATIENT/DOCTOR/ADMIN or DOCTOR/ADMIN)
- `GET /api/lab-results/{id}`, `GET /api/lab-results/patient/{patientId}`
- `GET /api/medical-records/{id}`, `GET /api/medical-records/patient/{patientId}`, `PUT /api/medical-records/{id}`
- `GET /api/prescriptions/{id}`, `GET /api/prescriptions/patient/{patientId}`
- `GET /api/patients/by-user/{userId}`
- `GET /api/users/{id}`, `PUT /api/users/{id}`
- `GET /api/messages/conversations?userId=`, `GET /api/messages/conversation/{userId}`, `GET /api/messages/{id}` and the message delete/read endpoints

Pure function-level gates RETAINED (true negatives at the function level — a
PATIENT is correctly denied on both `fixed` and `partial-auth`):

- `GET /api/users` (list all), `PUT /api/users/{id}/role`, reset-password: `hasRole('ADMIN')`
- `POST` create prescription / dispense: `hasAnyRole('DOCTOR','PHARMACIST','ADMIN')`, `hasAnyRole('PHARMACIST','ADMIN')`
- `POST` create medical record: `hasAnyRole('DOCTOR','ADMIN')`
- lab-results search: `hasAnyRole('LAB_TECH','ADMIN','DOCTOR')`
