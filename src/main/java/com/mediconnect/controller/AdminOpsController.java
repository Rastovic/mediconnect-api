package com.mediconnect.controller;

import com.mediconnect.dto.AdminDashboardDto;
import com.mediconnect.service.AdminOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Module C — System & Ops
//
// [A01] All endpoints under permitAll(). No role check anywhere.
//
//   GET  /dashboard              — counts + recent events (A03 via since param)
//   GET  /health                 — JVM/DB internals (A02)
//   GET  /config                 — env dump (existing #64 — moved here)
//   PUT  /config/{key}           — runtime mutation (A02 + A09)
//   POST /maintenance/run-sql    — arbitrary SQL execution (A03)
//   POST /maintenance/restart    — System.exit(0) (A04)
//   GET  /maintenance/backup     — mysqldump exec (A03 command injection)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AdminOpsService adminOpsService;
    private final AbstractEnvironment environment;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> dashboard(@RequestParam(required = false) String since) {
        return ResponseEntity.ok(adminOpsService.getDashboard(since));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(adminOpsService.getHealth());
    }

    // [A04] Existing config dump — moved from AdminController.
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(adminOpsService.getConfig(environment));
    }

    // [A02][A09] Runtime property mutation. Caller can disable interceptor logging.
    @PutMapping("/config/{key}")
    public ResponseEntity<Map<String, Object>> setConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.getOrDefault("value", "");
        return ResponseEntity.ok(adminOpsService.setConfig(environment, key, value));
    }

    // [A03] Arbitrary SQL execution.
    @PostMapping("/maintenance/run-sql")
    public ResponseEntity<Map<String, Object>> runSql(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminOpsService.runSql(body.getOrDefault("sql", "")));
    }

    // [A04] Hard restart — System.exit(0).
    @PostMapping("/maintenance/restart")
    public ResponseEntity<Map<String, Object>> restart() {
        adminOpsService.restart();
        return ResponseEntity.ok(Map.of("status", "restarting", "delayMs", 500));
    }

    // [A03] mysqldump via shell — command injection via dbName param.
    @GetMapping("/maintenance/backup")
    public ResponseEntity<String> backup(@RequestParam(defaultValue = "mediconnect_db") String dbName) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"backup.sql\"")
                .body(adminOpsService.backup(dbName));
    }
}
