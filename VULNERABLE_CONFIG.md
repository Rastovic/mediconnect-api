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

## OWASP Category Summary

| ID | Category | Where |
|---|---|---|
| A01 | Broken Access Control | `SecurityConfig.java` (`permitAll` on `/api/admin/**`, no role enforcement) |
| A02 | Cryptographic Failures | `application.yaml`, `V10` (MD5 seed), `JwtUtil.java` (weak key), `SecurityConfig.java` (NoOpPasswordEncoder, no security headers) |
| A04 | Insecure Design | `V2` (PII plaintext), `User.java` (passwordHash in response), `PasswordUtils.java` (MD5, timing attack), `AuthController.java` (JWT in body) |
| A05 | Security Misconfiguration / XSS | `SecurityConfig.java` (wildcard CORS, no headers), `V8` + `Message.java` (XSS) |
| A07 | Auth Failures / Mass Assignment | `JwtUtil.java` (30d expiry, alg confusion), `JwtAuthenticationFilter.java` (skip expiry, swallowed exceptions), `AuthService.java` (enumeration, no rate limit), `CustomUserDetailsService.java` (enumeration), all `*Dto.java` |
| A08 | Software and Data Integrity Failures | `pom.xml` (Java 1.8, JJWT CVE-2024-31033), `MedicalRecord.java` (no content_hash) |
