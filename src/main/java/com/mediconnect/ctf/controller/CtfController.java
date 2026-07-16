package com.mediconnect.ctf.controller;

import com.mediconnect.ctf.dto.CtfDtos.*;
import com.mediconnect.ctf.repository.CtfSecretRepository;
import com.mediconnect.ctf.service.CtfGatingService;
import com.mediconnect.ctf.service.CtfService;
import com.mediconnect.enums.Role;
import com.mediconnect.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * CTF platform API. Unlike the app under test, this surface is hardened:
 * every endpoint requires an authenticated user (plan §5.1). The rest of the
 * app runs permitAll(), so auth is enforced here explicitly rather than in
 * SecurityConfig - deliberately, so the CTF layer stays isolated from the
 * intentionally-broken security config.
 */
@RestController
@RequestMapping("/api/ctf")
@RequiredArgsConstructor
public class CtfController {

    private final CtfService ctf;
    private final CtfGatingService gating;
    private final CtfSecretRepository secrets;

    /**
     * A01/A04 auth-bypass target. Returns the flag only to a caller whose
     * authenticated identity is ADMIN. The app under test signs JWTs with a
     * hardcoded secret and derives the role from the DB by subject, so a student
     * forges a token for subject "admin" (A04 #16) - or otherwise escalates - to
     * reach this. This gate is enforced here in code because the rest of the app
     * is permitAll() and would not stop a PATIENT.
     */
    @GetMapping("/admin-secret")
    public ResponseEntity<Map<String, String>> adminSecret(@AuthenticationPrincipal UserPrincipal principal) {
        requireUserId(principal);
        if (principal.getUser().getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin identity required");
        }
        String flag = secrets.findByLabel("a04-hardcoded-jwt-signing-secret")
                .map(s -> s.getFlag())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "secret not seeded"));
        return ResponseEntity.ok(Map.of("flag", flag));
    }

    @GetMapping("/challenges")
    public ResponseEntity<List<ChallengeSummary>> challenges(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ctf.list(requireUserId(principal)));
    }

    @GetMapping("/challenges/{slug}")
    public ResponseEntity<ChallengeDetail> challenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug) {
        return ResponseEntity.ok(ctf.detail(requireUserId(principal), slug));
    }

    @PostMapping("/challenges/{slug}/submit")
    public ResponseEntity<SubmitResponse> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug,
            @RequestBody SubmitRequest body) {
        return ResponseEntity.ok(ctf.submit(requireUserId(principal), slug, body.flag()));
    }

    @PostMapping("/challenges/{slug}/hint")
    public ResponseEntity<Map<String, Integer>> hint(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug) {
        int used = ctf.revealHint(requireUserId(principal), slug);
        return ResponseEntity.ok(Map.of("hintsUsed", used));
    }

    @PostMapping("/challenges/{slug}/reset")
    public ResponseEntity<Void> reset(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug) {
        ctf.reset(requireUserId(principal), slug);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/progress")
    public ResponseEntity<Progress> progress(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ctf.progress(requireUserId(principal)));
    }

    /**
     * Full game re-seed (§12.4): clears the caller's progress + submissions and all
     * in-memory behavioral marks, so the whole board can be replayed. App data mutated
     * by destructive challenges is restored separately via `docker compose down -v`.
     */
    @PostMapping("/progress/reset")
    public ResponseEntity<Void> resetAll(@AuthenticationPrincipal UserPrincipal principal) {
        ctf.resetAll(requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<SettingsView> settings(@AuthenticationPrincipal UserPrincipal principal) {
        requireUserId(principal);
        List<SettingEntry> entries = gating.all().entrySet().stream()
                .map(e -> new SettingEntry(e.getKey(), e.getValue()))
                .toList();
        return ResponseEntity.ok(new SettingsView(entries));
    }

    @PutMapping("/settings")
    public ResponseEntity<SettingsView> updateSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody List<SettingEntry> body) {
        requireUserId(principal);
        for (SettingEntry e : body) {
            gating.put(e.key(), e.value());
        }
        return settings(principal);
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return principal.getUser().getId();
    }
}
