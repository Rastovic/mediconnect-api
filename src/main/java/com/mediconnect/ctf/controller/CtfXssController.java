package com.mediconnect.ctf.controller;

import com.mediconnect.ctf.repository.CtfSecretRepository;
import com.mediconnect.entity.Message;
import com.mediconnect.repository.MessageRepository;
import com.mediconnect.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side stored-XSS detection for A05 #55 (plan §5.3, §1) - no headless
 * browser. The app stores message content verbatim and the admin message view
 * renders it unescaped, so an executable vector stored in a message proves the
 * stored-XSS sink is reachable. This detector confirms such a payload exists in
 * the caller's sent messages and awards the flag.
 */
@RestController
@RequestMapping("/api/ctf/xss")
@RequiredArgsConstructor
public class CtfXssController {

    private final MessageRepository messages;
    private final CtfSecretRepository secrets;

    // Executable XSS vectors that survive only because content is stored unescaped.
    private static final Pattern XSS = Pattern.compile(
            "(?i)(<script\\b|javascript:|on(error|load|click|mouseover|focus|toggle)\\s*=" +
            "|<svg[^>]*\\bon\\w+\\s*=|<img[^>]*\\bonerror\\s*=|<iframe\\b)");

    @GetMapping("/check")
    public ResponseEntity<Map<String, String>> check(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        Long userId = principal.getUser().getId();

        boolean unescapedSinkReached = messages.findBySenderId(userId).stream()
                .map(Message::getContent)
                .filter(c -> c != null)
                .anyMatch(c -> XSS.matcher(c).find());

        if (!unescapedSinkReached) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No unescaped executable payload found in your messages yet. " +
                    "Post one via POST /api/messages, then re-check.");
        }
        String flag = secrets.findByLabel("a05-stored-xss-admin-bot")
                .map(s -> s.getFlag())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "secret not seeded"));
        return ResponseEntity.ok(Map.of(
                "detected", "stored XSS payload rendered unescaped in the admin context",
                "flag", flag));
    }
}
