# Vulnerable Configuration — MediConnect API

Grana: `vulnerable` | Svrha: OWASP Top 10 edukacija / demonstracija napada

---

## Dodani fajlovi

### `docker-compose.yml`

Pokreće lokalno okruženje sa dva servisa:

| Servis | Image | Port | Kredencijali |
|---|---|---|---|
| MySQL 8.0 | `mysql:8.0` | `3306` | root / root |
| phpMyAdmin | `phpmyadmin:latest` | `8082` | root / root |

```bash
docker-compose up -d
```

---

### `src/main/resources/application.yaml`

Potpuna konfiguracija aplikacije sa namernim ranjivostima označenim `[A02]`.

#### Ranjivosti

**1. Hardkodovani kredencijali baze [A02]**
```yaml
datasource:
  username: root
  password: root
```
Lozinka vidljiva u plain-text konfiguracionom fajlu u repozitorijumu.

---

**2. SQL šema curenje u logovima [A02]**
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
Kompletni SQL upiti sa parametrima ispisuju se u logove — otkriva strukturu baze.

---

**3. Destruktivne izmene šeme [A02]**
```yaml
jpa:
  hibernate:
    ddl-auto: update
```
Hibernate automatski menja šemu baze pri pokretanju — mogući gubitak podataka, zaobilazi Flyway kontrolu migracija.

---

**4. Actuator endpointi javno dostupni [A02]**
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
Svi actuator endpointi dostupni bez autentifikacije:
- `/actuator/env` — environment varijable i lozinke
- `/actuator/heapdump` — dump memorije JVM procesa
- `/actuator/beans` — interni Spring kontekst
- `/actuator/mappings` — sve URL rute aplikacije

---

**5. Stack trace ekspozicija klijentu [A02]**
```yaml
server:
  error:
    include-stacktrace: always
    include-message: always
    include-exception: true
```
Interni Java stack trace vraća se u HTTP odgovoru — otkriva putanje fajlova, klase, verzije biblioteka.

---

**6. Thymeleaf keš isključen [A02]**
```yaml
thymeleaf:
  cache: false
```
Podržava istraživanje Server-Side Template Injection (SSTI) ranjivosti.

---

---

## Flyway migracije — `src/main/resources/db/migration/`

### Šema tabela

| Migracija | Tabela | Opis |
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
| V10 | seed data | admin + patient1 + doctor1 sa MD5 lozinkama |

### Ranjivosti u migracijama

**7. PII plaintext bez enkripcije [A04] — V2**
```sql
insurance_number  VARCHAR(50),
date_of_birth     DATE,
blood_type        VARCHAR(5),
allergies         TEXT,
emergency_contact VARCHAR(255)
```
Osjetljivi zdravstveni podaci pacijenata pohranjeni nekriptovano — direktno čitljivi iz baze.

---

**8. Nema content_hash za medicinske nalaze [A08] — V5**
```sql
-- content_hash kolona namjerno izostavljena
attachment_path VARCHAR(500)
```
Bez hash-a nije moguće detektovati tamperovanje priloženih fajlova niti verificirati integritet dijagnoza i recepata.

---

**9. Stored XSS — messages.content bez sanitizacije [A05] — V8**
```sql
content TEXT  -- sirovi HTML/JS, bez escaping-a
```
Pohranjen sadržaj se prikazuje bez obrade — napadač može injektovati `<script>` tag koji se izvršava kod svakog primatelja.

---

**10. MD5 seed lozinke bez salta [A02] — V10**
```sql
-- admin123  → MD5 → 0192023a7bbd73250516f069df18b500
-- 12345     → MD5 → 827ccb0eea8a706c4c34a16891f84e7b
-- password  → MD5 → 5f4dcc3b5aa765d61d8327deb882cf99
```
Sve tri vrijednosti postoje u javnim rainbow tablicama — lozinke su trivijalno otkrivljive.

---

---

## JPA entiteti, repozitorijumi i DTOs

### Novi fajlovi

| Paket | Fajlovi |
|---|---|
| `com.mediconnect.enums` | `Role`, `AppointmentStatus`, `LabResultStatus`, `PrescriptionStatus` |
| `com.mediconnect.entity` | `User`, `Patient`, `Doctor`, `Appointment`, `MedicalRecord`, `LabResult`, `Prescription`, `Message`, `AuditLog` |
| `com.mediconnect.repository` | Po jedan repozitorijum za svaki entitet sa custom metodama |
| `com.mediconnect.dto` | Po jedan DTO za svaki entitet — isti za request i response |

`MediconnectApiApplication` je ažuriran sa `scanBasePackages`, `@EntityScan` i `@EnableJpaRepositories` za `com.mediconnect`.

### Ranjivosti

**11. passwordHash vidljiv u API odgovoru [A04] — `User.java`, `UserDto.java`**
```java
// Nema @JsonIgnore — hash lozinke vraća se u svakom GET /users/{id} odgovoru
private String passwordHash;
```

---

**12. PII podaci u API odgovoru bez maskovanja [A04] — `Patient.java`, `PatientDto.java`**
```java
// Svi podaci vraćaju se u plain textu — insurance, DOB, krvna grupa, alergije
private String insuranceNumber;
private LocalDate dateOfBirth;
private String bloodType;
private String allergies;
private String emergencyContact;
```

---

**13. Mass Assignment — isti DTO za request i response [A07] — svi `*Dto.java`**

Primjeri privilegovanih polja koja klijent može postaviti:

| DTO | Opasno polje | Efekat |
|---|---|---|
| `UserDto` | `role`, `active`, `failedLoginAttempts`, `lockedUntil` | Privilege escalation, unbanovanje |
| `PatientDto` | `userId` | Preuzimanje tuđeg profila |
| `AppointmentDto` | `status` | Direktno odobravanje termina |
| `PrescriptionDto` | `status`, `pharmacistId` | Lažno izdavanje lijeka |
| `MessageDto` | `senderId` | Lažiranje pošiljatelja poruke |
| `AuditLogDto` | `userId`, `ipAddress` | Manipulacija audit tragom |

---

**14. XSS — `Message.content` bez sanitizacije [A05] — `Message.java`, `MessageDto.java`**
```java
// Sadržaj pohranjen i vraćen bez obrade — Stored XSS
private String content;
```

---

**15. Integritet fajlova neprovjerliv [A08] — `MedicalRecord.java`, `MedicalRecordDto.java`**
```java
// Nema contentHash — tamperovanje attachment-a nedetektabilno
private String attachmentPath;
```

---

## Pregled OWASP kategorija

| ID | Kategorija | Gdje |
|---|---|---|
| A02 | Cryptographic Failures | `application.yaml` (hardkodovani kredencijali, SQL log), `V10` (MD5 seed) |
| A04 | Insecure Design | `V2` (PII plaintext), `User.java` (passwordHash u odgovoru), `Patient.java` (PII u odgovoru) |
| A05 | Security Misconfiguration / XSS | `V8`, `Message.java` — content bez sanitizacije |
| A07 | Auth Failures / Mass Assignment | Svi `*Dto.java` — isti DTO za request i response |
| A08 | Software and Data Integrity Failures | `pom.xml` (Java 1.8), `V5` + `MedicalRecord.java` (nema content_hash) |
