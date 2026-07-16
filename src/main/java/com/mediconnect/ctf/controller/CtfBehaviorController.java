package com.mediconnect.ctf.controller;

import com.mediconnect.ctf.CtfBehaviorRegistry;
import com.mediconnect.ctf.repository.CtfSecretRepository;
import com.mediconnect.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Detector for behavioral CTF challenges (plan §12.1). A challenge's illegal
 * action, when it succeeds, calls CtfBehaviorRegistry.mark(slug) from inside the
 * vulnerable endpoint. This returns the flag once that has happened - the flag
 * exists only as proof the illegal action worked, there is no string to read.
 */
@RestController
@RequestMapping("/api/ctf/behavior")
@RequiredArgsConstructor
public class CtfBehaviorController {

    private final CtfSecretRepository secrets;

    @GetMapping("/{slug}")
    public ResponseEntity<Map<String, String>> check(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug) {
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (!CtfBehaviorRegistry.reached(slug)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Not detected yet. Perform the illegal action, then re-check.");
        }
        String flag = secrets.findByLabel(slug)
                .map(s -> s.getFlag())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "secret not seeded"));
        return ResponseEntity.ok(Map.of("detected", slug, "flag", flag));
    }
}
