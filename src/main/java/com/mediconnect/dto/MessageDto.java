package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A05] XSS: content field accepts and returns raw HTML/JS without sanitization.
// [A07] Same DTO for request and response — client can spoof the senderId and
//        send messages on behalf of another user.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private Long id;
    // [A07] Client can supply another user's senderId — sender spoofing
    private Long senderId;
    private String senderEmail;
    private Long receiverId;
    // [A05] Raw HTML/JS content accepted and returned without any processing
    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private boolean read;
}
