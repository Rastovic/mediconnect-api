package com.mediconnect.service;

import com.mediconnect.entity.Message;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.MessageRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Module F — Broadcast Communications.
//
// One endpoint creates a Message row per recipient. The recipient inbox
// (MessagesPage.tsx:293) already renders Message.content via
// dangerouslySetInnerHTML — see existing vuln #84. So a broadcast that
// writes HTML straight into Message.content is stored XSS across every
// matching account with no new sink to wire on the recipient side.
@Service
@RequiredArgsConstructor
public class AdminBroadcastService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SanitizerService sanitizer;

    // [A05][A07] Broadcast — `subject` + `html` written verbatim into
    //              Message.content; recipient inbox renders via dangerouslySetInnerHTML.
    //              `senderId` taken from the body, so the broadcast appears in every
    //              recipient's inbox attributed to a user the attacker chose
    //              (compounds the existing senderId spoofing surface, #88).
    public Map<String, Object> broadcast(Map<String, Object> body) {
        String subject = sanitizer.text((String) body.getOrDefault("subject", ""));
        String html    = sanitizer.clean((String) body.getOrDefault("html", ""));
        // [A07] senderId from body — no JWT lookup. Default to user #1 (admin
        //        from the seed). Attacker can attribute the broadcast to any
        //        user, e.g. their own doctor account.
        Long senderId = body.get("senderId") instanceof Number
                ? ((Number) body.get("senderId")).longValue()
                : 1L;

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) body.getOrDefault("roles", List.of());
        boolean sendToAll = roles.isEmpty();

        User sender = userRepository.findById(senderId).orElse(null);

        List<User> recipients;
        if (sendToAll) {
            // [A04] No upper bound. With ~9 seeded accounts this is a trivial
            //        broadcast; on a real deployment with N users it sends N
            //        messages in one synchronous request — DoS class issue.
            recipients = userRepository.findAll();
        } else {
            recipients = userRepository.findAll().stream()
                    .filter(u -> roles.stream().anyMatch(r -> r.equalsIgnoreCase(u.getRole().name())))
                    .collect(Collectors.toList());
        }

        // [A05] subject + html stored verbatim. Compounds with the existing
        //        XSS sink at MessagesPage.tsx:293 — opening the inbox executes
        //        whatever script the broadcaster wrote.
        String body_ = "<h3>" + subject + "</h3>" + html;

        int sent = 0;
        for (User recipient : recipients) {
            Message m = Message.builder()
                    .sender(sender != null ? sender : recipient)
                    .receiver(recipient)
                    .content(body_)
                    .sentAt(LocalDateTime.now())
                    .build();
            messageRepository.save(m);
            sent++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("roles", sendToAll ? List.of("ALL") : roles);
        result.put("senderId", sender != null ? sender.getId() : null);
        result.put("preview", body_.length() > 200 ? body_.substring(0, 200) + "…" : body_);
        return result;
    }

    // [A08][A09] Redact — overwrites Message.content in place. No history
    //              column, no `redacted_at`, no `redacted_by`. Audit trail loses
    //              the original message body; the only record of what it said
    //              was the response cache for the original send (which
    //              POST /admin/logs/clear or DELETE /admin/logs/{id} can erase).
    public Map<String, Object> redact(Long id, Map<String, Object> body) {
        Message m = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found: " + id));
        String replacement = body != null && body.get("content") instanceof String
                ? (String) body.get("content")
                : "<em>[redacted]</em>";
        m.setContent(replacement);
        messageRepository.save(m);

        return Map.of(
                "id", id,
                "redacted", true,
                "newContent", replacement
        );
    }

    public List<Map<String, Object>> listBroadcasts() {
        // Convention: a "broadcast" is any message where sender_id == receiver_id
        // (the broadcaster-as-self pattern above) OR where content starts with
        // the broadcast subject wrapper. Cheap heuristic for the UI's history list.
        return messageRepository.findAll().stream()
                .filter(m -> m.getContent() != null && m.getContent().startsWith("<h3>"))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .limit(50)
                .map(this::toMessageMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> previewRecipients(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return userRepository.findAll().stream()
                    .map(this::toRecipientMap)
                    .collect(Collectors.toList());
        }
        return userRepository.findAll().stream()
                .filter(u -> roles.stream().anyMatch(r -> r.equalsIgnoreCase(u.getRole().name())))
                .map(this::toRecipientMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMessageMap(Message m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("senderId", m.getSender() != null ? m.getSender().getId() : null);
        map.put("receiverId", m.getReceiver() != null ? m.getReceiver().getId() : null);
        // [A05] content returned verbatim including raw HTML
        map.put("content", m.getContent());
        map.put("sentAt", m.getSentAt());
        return map;
    }

    private Map<String, Object> toRecipientMap(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("email", u.getEmail());
        map.put("role", u.getRole() != null ? u.getRole().name() : null);
        return map;
    }

    // [A07] Sanity helper — kept so the controller can offer a role dropdown.
    public List<String> availableRoles() {
        return java.util.Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toList());
    }
}
