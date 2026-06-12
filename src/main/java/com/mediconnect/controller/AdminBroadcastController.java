package com.mediconnect.controller;

import com.mediconnect.service.AdminBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module F — Broadcast Communications
//
// [A01] All endpoints under permitAll(). No role check.
//
//   POST /admin/broadcast               — store HTML into Message.content for many recipients (A05)
//   GET  /admin/broadcast/history       — recent broadcasts
//   POST /admin/broadcast/preview       — preview recipients matching role filter
//   POST /admin/messages/{id}/redact    — overwrite message content, no history (A08+A09)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBroadcastController {

    private final AdminBroadcastService adminBroadcastService;

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminBroadcastService.broadcast(body));
    }

    @GetMapping("/broadcast/history")
    public ResponseEntity<List<Map<String, Object>>> history() {
        return ResponseEntity.ok(adminBroadcastService.listBroadcasts());
    }

    @PostMapping("/broadcast/preview")
    public ResponseEntity<List<Map<String, Object>>> preview(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) body.getOrDefault("roles", List.of());
        return ResponseEntity.ok(adminBroadcastService.previewRecipients(roles));
    }

    // [A08][A09] Redact — overwrites content in place; no original preserved.
    @PostMapping("/messages/{id}/redact")
    public ResponseEntity<Map<String, Object>> redact(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminBroadcastService.redact(id, body));
    }
}
