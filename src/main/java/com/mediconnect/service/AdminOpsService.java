package com.mediconnect.service;

import com.mediconnect.dto.AdminDashboardDto;
import com.mediconnect.dto.AuditLogDto;
import com.mediconnect.entity.AuditLog;
import com.mediconnect.repository.AuditLogRepository;
import com.mediconnect.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOpsService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // [A01][A03] Counts pulled via raw SQL — caller-controlled `since` parameter
    //              is concatenated into the WHERE clause.
    public AdminDashboardDto getDashboard(String since) {
        Map<String, Long> userCounts = countByRole();
        Map<String, Long> apptCounts = countByStatus("appointments", "status");
        Map<String, Long> rxCounts   = countByStatus("prescriptions", "status");
        Map<String, Long> refillCounts = countByStatus("refill_requests", "status");

        // [A03] `since` concatenated directly — SELECT count UNION exfil possible
        String failedLoginsSql = "SELECT COUNT(*) FROM users WHERE failed_login_attempts > 0";
        boolean hasSince = since != null && !since.isBlank();
        if (hasSince) {
            failedLoginsSql += " AND created_at >= ?1";
        }
        Query flq = entityManager.createNativeQuery(failedLoginsSql);
        if (hasSince) {
            flq.setParameter(1, since);
        }
        long failedLogins = ((Number) flq.getSingleResult()).longValue();

        long locked = userRepository.findAll().stream()
                .filter(u -> u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now()))
                .count();

        long totalLogs = auditLogRepository.count();

        List<AuditLog> recent = auditLogRepository.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        return AdminDashboardDto.builder()
                .userCountsByRole(userCounts)
                .appointmentCountsByStatus(apptCounts)
                .prescriptionCountsByStatus(rxCounts)
                .refillCountsByStatus(refillCounts)
                .failedLoginsLast24h(failedLogins)
                .lockedAccounts(locked)
                .totalAuditLogs(totalLogs)
                .recentEvents(recent.stream().map(this::toLogDto).collect(Collectors.toList()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> countByRole() {
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT role, COUNT(*) FROM users GROUP BY role")
                .getResultList();
        return rows.stream().collect(Collectors.toMap(
                r -> r[0].toString(),
                r -> ((Number) r[1]).longValue()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> countByStatus(String table, String column) {
        // [A03] table/column names are not user input here, but the same util
        //        is reused below — keep an eye out for future callers.
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT " + column + ", COUNT(*) FROM " + table + " GROUP BY " + column)
                .getResultList();
        return rows.stream()
                .filter(r -> r[0] != null)
                .collect(Collectors.toMap(
                        r -> r[0].toString(),
                        r -> ((Number) r[1]).longValue()));
    }

    // [A02] Health endpoint dumps JVM internals — class path, command-line args,
    //        installed agents — and any environment variable the JVM saw at boot.
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        health.put("status", "UP");
        health.put("javaVersion", System.getProperty("java.version"));
        health.put("javaVendor", System.getProperty("java.vendor"));
        // [A02] class path reveals everything on the classpath incl. third-party jars
        health.put("classPath", System.getProperty("java.class.path"));
        health.put("javaHome", System.getProperty("java.home"));
        health.put("osName", System.getProperty("os.name"));
        health.put("osArch", System.getProperty("os.arch"));
        health.put("user.dir", System.getProperty("user.dir"));
        health.put("memoryMaxMb", rt.maxMemory() / (1024 * 1024));
        health.put("memoryTotalMb", rt.totalMemory() / (1024 * 1024));
        health.put("memoryFreeMb", rt.freeMemory() / (1024 * 1024));
        health.put("availableProcessors", rt.availableProcessors());

        // [A02] DB latency probed via native query — exposes DB up/down state
        try {
            long start = System.nanoTime();
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            health.put("dbLatencyMs", (System.nanoTime() - start) / 1_000_000.0);
            health.put("db", "UP");
        } catch (Exception e) {
            health.put("db", "DOWN");
            // [A10] swallowed exception text disclosed to caller
            health.put("dbError", e.getMessage());
        }
        return health;
    }

    // [A04] Existing config dump — same behaviour as `AdminController.getConfig`,
    //        moved here so the old controller can be retired.
    public Map<String, Object> getConfig(AbstractEnvironment environment) {
        Map<String, Object> props = new LinkedHashMap<>();
        environment.getPropertySources().stream()
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> (EnumerablePropertySource<?>) ps)
                .forEach(ps -> Arrays.stream(ps.getPropertyNames())
                        .forEach(name -> props.put(name, ps.getProperty(name))));
        return props;
    }

    // Retired: runtime property mutation could disable audit logging and is
    // impossible to make safe. Configuration is immutable at runtime.
    public Map<String, Object> setConfig(AbstractEnvironment environment, String key, String value) {
        throw new UnsupportedOperationException("Runtime configuration override is disabled");
    }

    // Retired: arbitrary SQL execution is equivalent to DB RCE and has no safe form.
    public Map<String, Object> runSql(String sql) {
        throw new UnsupportedOperationException("Direct SQL execution is disabled");
    }

    // Retired: process-level restart via System.exit is a self-inflicted DoS.
    public void restart() {
        throw new UnsupportedOperationException("Runtime restart is disabled");
    }

    // Backup is invoked with a fixed, hardcoded database name and NO shell,
    // so no part of the command comes from the request (command-injection fix).
    public String backup(String dbNameIgnored) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "mysqldump", "--no-create-db", "--single-transaction", "mediconnect_db");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "Backup failed";
        }
    }

    private AuditLogDto toLogDto(AuditLog l) {
        return AuditLogDto.builder()
                .id(l.getId())
                .userId(l.getUser() != null ? l.getUser().getId() : null)
                .action(l.getAction())
                .entityType(l.getEntityType())
                .entityId(l.getEntityId())
                .ipAddress(l.getIpAddress())
                .userAgent(l.getUserAgent())
                .details(l.getDetails())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
