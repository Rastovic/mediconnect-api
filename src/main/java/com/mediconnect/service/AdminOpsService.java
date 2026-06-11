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
        if (since != null && !since.isBlank()) {
            failedLoginsSql += " AND created_at >= '" + since + "'";
        }
        long failedLogins = ((Number) entityManager.createNativeQuery(failedLoginsSql).getSingleResult()).longValue();

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

    // [A02][A09] Runtime mutation of any Spring property. Caller can flip
    //              `app.debug.enabled`, `logging.level.root`, server config —
    //              and most relevantly turn LoggingInterceptor output off so
    //              their own subsequent actions never reach audit_logs.
    public Map<String, Object> setConfig(AbstractEnvironment environment, String key, String value) {
        MutablePropertySources sources = environment.getPropertySources();
        Map<String, Object> overrides = new HashMap<>();
        // Reuse an existing overlay source if we've added one already
        if (sources.contains("admin-runtime-overrides")) {
            Object src = sources.get("admin-runtime-overrides").getSource();
            if (src instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = (Map<String, Object>) src;
                overrides.putAll(existing);
            }
            sources.remove("admin-runtime-overrides");
        }
        overrides.put(key, value);
        // [A02] Overlay added at the top of the property source list so it
        //        overrides application.yaml and OS env vars.
        sources.addFirst(new MapPropertySource("admin-runtime-overrides", overrides));
        return Map.of("key", key, "value", value, "overrideCount", overrides.size());
    }

    // [A03] Run arbitrary SQL with the application's DB user (root in dev).
    //        SELECT returns rows; UPDATE/DELETE/DROP return affected count.
    //        Equivalent to RCE for the database.
    @Transactional
    public Map<String, Object> runSql(String sql) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", sql);
        try {
            String trimmed = sql.trim().toLowerCase();
            if (trimmed.startsWith("select") || trimmed.startsWith("show") || trimmed.startsWith("describe")) {
                Query q = entityManager.createNativeQuery(sql);
                @SuppressWarnings("unchecked")
                List<Object> rows = q.getResultList();
                List<List<Object>> safeRows = rows.stream().map(r -> {
                    if (r == null) return List.<Object>of();
                    if (r.getClass().isArray()) return Arrays.asList((Object[]) r);
                    return List.<Object>of(r);
                }).collect(Collectors.toList());
                result.put("type", "rows");
                result.put("rowCount", safeRows.size());
                result.put("rows", safeRows);
            } else {
                int affected = entityManager.createNativeQuery(sql).executeUpdate();
                result.put("type", "update");
                result.put("affected", affected);
            }
        } catch (Exception e) {
            // [A10] raw SQL exception text returned — confirms table/column names
            result.put("type", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    // [A04] Hard restart — invokes System.exit so the supervisor restarts the JVM.
    //        No role check, no rate limit. Reachable by any caller.
    public void restart() {
        // Run on a separate thread so the HTTP response can still be flushed
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "admin-restart").start();
    }

    // [A03] Command Injection — `mysqldump` invoked with the dbName argument
    //        spliced into the command list. If the caller controls dbName
    //        (which they do — it's a query param) they can append shell
    //        metacharacters via env-var defaults / mysql client tricks.
    //
    //  Demo payload:
    //    GET /api/admin/maintenance/backup?dbName=mediconnect_db;cat%20/etc/passwd
    //  Then look in the response body for the appended output.
    public String backup(String dbName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "mysqldump --no-create-db " + dbName);
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
            // [A10] raw exception text returned to caller
            return "Backup failed: " + e.getMessage();
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
