package com.mediconnect.controller;

import com.mediconnect.dto.UserDto;
import com.mediconnect.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// [A01] No @PreAuthorize or role check anywhere in this controller.
// [A04] passwordHash field included in every response via UserDto.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // [A01] Broken Access Control — returns all users (including passwordHash)
    //        to any caller regardless of role. No pagination, no field filtering.
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    // [A01] IDOR — no check that the authenticated user is allowed to read this id.
    //        Patient A can fetch Patient B's profile, including passwordHash.
    // [A04] Response includes passwordHash in plain text.
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    // [A01] Mass Assignment — 'role' value comes directly from the request body
    //        with no server-side validation or whitelist.
    //        Any authenticated (or unauthenticated, given SecurityConfig) caller
    //        can send {"role":"ADMIN"} and escalate any account to ADMIN.
    @PutMapping("/{id}/role")
    public ResponseEntity<UserDto> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        // [A01] role taken directly from body — Role.valueOf() will accept any valid enum name
        String role = body.get("role");
        return ResponseEntity.ok(userService.updateRole(id, role));
    }

    // [A07] Unsafe HTTP method — DELETE operation exposed as GET.
    //        A malicious link or <img src="/api/users/delete/1"> deletes a user
    //        without the victim's knowledge (no CSRF token required for GET).
    // [A01] No authorization check — any caller can delete any user.
    @GetMapping("/delete/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
