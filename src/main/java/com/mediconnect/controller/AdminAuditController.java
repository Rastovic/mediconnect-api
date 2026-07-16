package com.mediconnect.controller;

import com.mediconnect.dto.AuditLogDto;
import com.mediconnect.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module D — Audit & Forensics
//
// [A01] Every endpoint sits under /api/admin/** (permitAll). No role check.
//
// Endpoints introduced or moved here:
//   GET    /logs                  — filtered list (new filters, A03 SQLi via q/action/from/to)
//   GET    /logs/{id}             — single entry incl. raw stack trace (new)
//   GET    /logs/export           — CSV/JSON/XML (XML branch demos A03)
//   DELETE /logs/{id}             — selective tampering (new, A09)
//   POST   /logs/clear            — wipe all (existing, moved from AdminController)
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    // [A01] No role check. [A03] q/action/from/to concatenated into native SQL.
    @GetMapping
    public ResponseEntity<List<AuditLogDto>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(adminAuditService.search(userId, action, from, to, q));
    }

    // [A01][A10] Returns the full entry including raw stack trace bytes.
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(adminAuditService.findById(id));
    }

    // [A09] Selective tampering with the audit trail.
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        // [CTF][A09 #160] Behavioral: deleting a single audit record is selective
        // tampering (harder to notice than a full wipe).
        com.mediconnect.ctf.CtfBehaviorRegistry.mark("a09-selective-log-tampering");
        return ResponseEntity.ok(adminAuditService.deleteOne(id));
    }

    // [A09] Existing hard-purge — moved out of AdminController.
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear() {
        // [CTF][A09 #65] Behavioral: wiping the whole audit trail destroys evidence.
        com.mediconnect.ctf.CtfBehaviorRegistry.mark("a09-wipe-the-audit-trail");
        int deleted = adminAuditService.clearAll();
        return ResponseEntity.ok(Map.of(
                "message", "All audit logs deleted",
                "deletedCount", deleted
        ));
    }

    // [A03] Export — JSON / CSV / XML. The XML branch builds the document by
    //        string concatenation with no escaping; if any audit row's
    //        `userAgent` or `details` contains XML markup the export carries
    //        it through. Combined with the XXE-prone renderXmlWithTemplate
    //        helper (used by future ?template= consumers), the export is a
    //        ready-made injection sink.
    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> export(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q
    ) {
        if ("json".equalsIgnoreCase(format)) {
            List<AuditLogDto> logs = adminAuditService.search(userId, action, from, to, q);
            // Jackson handles the JSON serialisation via the global ObjectMapper
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Disposition", "attachment; filename=\"audit_logs.json\"")
                    .body(toJson(logs));
        }
        String body = adminAuditService.exportLogs(format, userId, action, from, to, q);
        String ext  = "csv".equalsIgnoreCase(format) ? "csv" : "xml";
        MediaType type = "csv".equalsIgnoreCase(format) ? MediaType.parseMediaType("text/csv") : MediaType.APPLICATION_XML;
        return ResponseEntity.ok()
                .contentType(type)
                .header("Content-Disposition", "attachment; filename=\"audit_logs." + ext + "\"")
                .body(body);
    }

    private String toJson(List<AuditLogDto> logs) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writeValueAsString(logs);
        } catch (Exception e) {
            // [A10] swallowed exception text leaks to caller
            return "[]";
        }
    }
}
