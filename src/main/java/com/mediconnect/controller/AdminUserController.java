package com.mediconnect.controller;

import com.mediconnect.dto.AdminUserDetailDto;
import com.mediconnect.dto.ImpersonationResponseDto;
import com.mediconnect.dto.ResetPasswordResponseDto;
import com.mediconnect.dto.UserDto;
import com.mediconnect.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module A — User & Account Management
//
// [A01] Every endpoint sits under /api/admin/** which SecurityConfig maps to
//        permitAll(). No @PreAuthorize, no manual role inspection. PATIENT /
//        LAB_TECH / unauthenticated callers reach every method below.
//
// Endpoints introduced or kept here:
//   GET    /users                       — list (existing, now with filters)
//   GET    /users/{id}                  — detail + recent logs (new)
//   POST   /users                       — create any role (existing, A07)
//   PUT    /users/{id}                  — mass-assign every field except role (new, A07)
//   DELETE /users/{id}                  — hard delete (new, A01+A09)
//   POST   /users/{id}/unlock           — reset lockout/failed counter (new, A07)
//   POST   /users/{id}/reset-password   — issue plaintext password (new, A02)
//   POST   /users/{id}/impersonate      — mint JWT for any user (new, A01+A07)
//   POST   /users/bulk-delete           — unbounded batch delete (new, A04+A09)
//   PATCH  /users/{id}/toggle           — flip active flag (existing, A01)
//
// Role updates remain on UserController.PUT /users/{id}/role for UI compatibility
// with the existing AdminPage.tsx Change-Role modal (see ADMIN_VIEW_PLAN.md §5.7).
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    // [A01] No role check — full user list including passwordHash for every account.
    //        MessagesPage.tsx:112 also consumes this; changing the response shape
    //        requires a coordinated UI edit.
    @GetMapping
    public ResponseEntity<List<UserDto>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean lockedOnly,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(adminUserService.findAll(role, active, lockedOnly, q));
    }

    // [A01] No role check, no ownership check — any caller reads any user's
    //        detail incl. recent audit log entries and last-login IP.
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailDto> detail(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getDetail(id));
    }

    // [A07] Mass Assignment — 'role' from body. [A06] MD5(password).
    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(201).body(adminUserService.createUser(body));
    }

    // [A07] Patches every field except role. Caller may write passwordHash directly,
    //        clear lockedUntil, reset failedLoginAttempts, flip active, etc.
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminUserService.updateUser(id, body));
    }

    // [A01][A09] Hard delete with no soft-delete, no archive. Deletion of an
    //             ADMIN user is allowed.
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.deleteUser(id));
    }

    // [A07] Unlocks an account with no rate-limit on the unlock itself —
    //        defeats the brute-force lockout protection in AuthService.
    @PostMapping("/{id}/unlock")
    public ResponseEntity<UserDto> unlock(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.unlock(id));
    }

    // [A02] Returns the new plaintext password in the response (and lets it
    //        flow into audit_logs.details via the request/response cache).
    // [A07] Accepts caller-supplied newPassword — admin can set any password directly.
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponseDto> resetPassword(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String supplied = (body != null) ? body.get("newPassword") : null;
        return ResponseEntity.ok(adminUserService.resetPassword(id, supplied));
    }

    // Impersonation is a high-risk operation: it is recorded in the append-only audit
    // log (acting admin, target user, and the supplied justification). The reason is
    // optional on the wire but the event is always audited.
    @PostMapping("/{id}/impersonate")
    public ResponseEntity<ImpersonationResponseDto> impersonate(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : null;
        return ResponseEntity.ok(adminUserService.impersonate(id, reason));
    }

    // [A04][A09] Unbounded batch delete. No upper limit, no per-id audit entry.
    @PostMapping("/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, List<Long>> body) {
        return ResponseEntity.ok(adminUserService.bulkDelete(body.getOrDefault("ids", List.of())));
    }

    // [A01] Existing endpoint — flips the active flag with no role check.
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<UserDto> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.toggleUser(id));
    }
}
