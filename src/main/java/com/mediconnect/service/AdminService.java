package com.mediconnect.service;

import com.mediconnect.dto.AuditLogDto;
import com.mediconnect.dto.UserDto;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.AuditLogRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordUtils passwordUtils;

    // [A01] No role check — any authenticated caller (PATIENT, DOCTOR, LAB_TECH)
    //        can retrieve the complete user list, including passwordHash values.
    //        The SecurityConfig.permitAll() makes this accessible even without a token.
    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // [A07] Mass Assignment — role is taken directly from the request map and written
    //        into the new User entity without any server-side validation or whitelist.
    //
    //  Attack:
    //    POST /api/admin/users {"username":"hacker","email":"h@x.com","password":"pass","role":"ADMIN"}
    //    → creates a fully privileged ADMIN account with no authorization check.
    //
    //  Compound vulnerability:
    //    This endpoint is under /api/admin/** which SecurityConfig maps to permitAll(),
    //    so the attacker does not even need a valid JWT token.
    //
    // [A04] Password hashed with MD5 (no salt) via PasswordUtils.hashPassword().
    public UserDto createUser(Map<String, String> body) {
        // [A07] role taken verbatim from the request body — no whitelist, no role enforcement
        String rawRole = body.getOrDefault("role", "PATIENT");

        User user = User.builder()
                .username(body.get("username"))
                .email(body.get("email"))
                .passwordHash(passwordUtils.hashPassword(body.get("password")))
                .role(Role.valueOf(rawRole))   // [A07] any enum value accepted, including ADMIN
                .active(true)
                .createdAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .build();

        return toDto(userRepository.save(user));
    }

    // [A02] Cryptographic / Sensitive Data Exposure — returns the complete set of
    //        resolved Spring Environment properties, including:
    //          spring.datasource.password  → database root password
    //          spring.datasource.url       → internal DB host and schema name
    //          server.port, management.*   → infrastructure topology
    //          Any env variable injected at runtime (DB_PASS, JWT_SECRET, etc.)
    //
    //  The /actuator/env endpoint already exposes this (see application.yaml),
    //  but this endpoint bypasses even the minimal Actuator access controls and
    //  returns raw unmasked values rather than the actuator's partially redacted output.
    public Map<String, Object> getConfig(AbstractEnvironment environment) {
        Map<String, Object> props = new LinkedHashMap<>();

        // [A02] Iterates all property sources — includes application.yaml,
        //        system environment variables, JVM system properties, and
        //        any Spring Cloud Config / Vault values if present.
        environment.getPropertySources().stream()
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> (EnumerablePropertySource<?>) ps)
                .forEach(ps -> Arrays.stream(ps.getPropertyNames())
                        .forEach(name -> props.put(name, ps.getProperty(name))));

        return props;
    }

    // [A09] Security Logging and Monitoring Failures — permanently destroys the entire
    //        audit trail without any authorization check, confirmation step, or backup.
    //
    //  What is lost:
    //    - Evidence of unauthorized access (login attempts, IDOR reads)
    //    - Evidence of data modification (role escalation, record tampering)
    //    - Forensic timeline needed for incident response
    //    - Compliance records required by HIPAA / GDPR for medical data access
    //
    //  Attack scenario:
    //    1. Attacker exploits IDOR, harvests patient records, escalates their own role to ADMIN.
    //    2. Attacker calls POST /api/admin/logs/clear to erase all evidence.
    //    3. Security team has no log data to detect or investigate the breach.
    //
    //  Secure implementation would require:
    //    - ADMIN role enforcement
    //    - Separate immutable audit sink (append-only DB table, SIEM, write-once S3 bucket)
    //    - At minimum a soft-delete with supervisor approval workflow
    public int clearAllLogs() {
        // [A09] deleteAll() is a hard delete — no soft-delete, no archive, no backup
        long count = auditLogRepository.count();
        auditLogRepository.deleteAll();
        return (int) count;
    }

    // [A01] No ownership or role check — any caller can flip any user's active flag.
    //        A PATIENT can disable the ADMIN account; no JWT role is inspected.
    public UserDto toggleUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        return toDto(userRepository.save(user));
    }

    public List<AuditLogDto> getAllLogs() {
        // [A01] No role check — any caller can read the full audit trail
        return auditLogRepository.findAll()
                .stream()
                .map(log -> AuditLogDto.builder()
                        .id(log.getId())
                        .userId(log.getUser() != null ? log.getUser().getId() : null)
                        .action(log.getAction())
                        .entityType(log.getEntityType())
                        .entityId(log.getEntityId())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .details(log.getDetails())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private UserDto toDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                // [A04] passwordHash included — no @JsonIgnore
                .passwordHash(u.getPasswordHash())
                .role(u.getRole())
                .active(u.getActive())
                .createdAt(u.getCreatedAt())
                .failedLoginAttempts(u.getFailedLoginAttempts())
                .lockedUntil(u.getLockedUntil())
                .build();
    }
}
