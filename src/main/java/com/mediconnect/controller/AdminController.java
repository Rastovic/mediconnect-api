package com.mediconnect.controller;

import com.mediconnect.dto.AuditLogDto;
import com.mediconnect.dto.UserDto;
import com.mediconnect.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// [A01] /api/admin/** is mapped to permitAll() in SecurityConfig — no authentication required.
//        Even endpoints that should be ADMIN-only are fully open to all callers.
// [A02] GET /config returns raw Environment properties including datasource.password.
// [A07] POST /users accepts 'role' from the request body — ADMIN account creation.
// [A09] POST /logs/clear permanently destroys the audit trail without authorization.
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AbstractEnvironment environment;

    // [A01] Broken Access Control — endpoint sits under /api/admin/** but SecurityConfig
    //        maps that prefix to permitAll(). No @PreAuthorize, no manual role check.
    //        Any caller (unauthenticated, PATIENT, LAB_TECH) gets the full user list
    //        including passwordHash, failedLoginAttempts, and lockedUntil timestamps.
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // [A07] Mass Assignment — 'role' field in the request body is written directly
    //        into the new User entity. No whitelist, no caller-role enforcement.
    //
    //  Attack — create an ADMIN account without any existing ADMIN token:
    //    POST /api/admin/users
    //    {"username":"attacker","email":"att@x.com","password":"pass","role":"ADMIN"}
    //    → valid ADMIN account created; attacker can now call any admin endpoint.
    //
    //  Compound effect: /api/admin/** is permitAll() → no token needed at all.
    @PostMapping("/users")
    public ResponseEntity<UserDto> createUser(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(201).body(adminService.createUser(body));
    }

    // [A02] Sensitive Data Exposure — returns the complete resolved Spring Environment,
    //        spanning application.yaml, OS environment variables, and JVM system properties.
    //
    //  Exposed values include:
    //    spring.datasource.password  → root database password
    //    spring.datasource.url       → internal DB host, port, and schema
    //    management.endpoints.*      → actuator configuration
    //    Any runtime env var: DB_PASS, JWT_SECRET, MAIL_PASSWORD, CLOUD_API_KEY, …
    //
    //  Unlike /actuator/env (which partially masks sensitive values with "******"),
    //  this endpoint returns all values as plain text with no redaction at all.
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(adminService.getConfig(environment));
    }

    // [A09] Security Logging and Monitoring Failures — permanently deletes the entire
    //        audit log table with no authorization check, no confirmation, and no backup.
    //
    //  Impact:
    //    - Destroys all evidence of unauthorized access and data exfiltration
    //    - Eliminates HIPAA / GDPR-mandated access logs for medical record reads
    //    - Removes the forensic timeline required for incident response
    //
    //  Attack chain:
    //    1. Exploit IDOR to read patient records and escalate role to ADMIN
    //    2. POST /api/admin/logs/clear → erase all evidence
    //    3. Security team finds no log data — breach is undetectable
    //
    // [A01] No role check — a PATIENT can call this endpoint and wipe the audit trail.
    @PostMapping("/logs/clear")
    public ResponseEntity<Map<String, Object>> clearLogs() {
        int deleted = adminService.clearAllLogs();
        return ResponseEntity.ok(Map.of(
                "message", "All audit logs deleted",
                "deletedCount", deleted
        ));
    }

    // [A01] No role check — any caller reads the full audit trail
    @GetMapping("/logs")
    public ResponseEntity<List<AuditLogDto>> getLogs() {
        return ResponseEntity.ok(adminService.getAllLogs());
    }
}
