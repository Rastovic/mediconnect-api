package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Same DTO for request and response — a client-created audit log entry
//        can contain a forged userId, ipAddress and action.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    // [A07] Client can forge the userId in the audit log
    private Long userId;
    private String action;
    private String entityType;
    private Long entityId;
    // [A07] IP address taken from the request — can be spoofed via X-Forwarded-For
    private String ipAddress;
    private String userAgent;
    private String details;
    private LocalDateTime createdAt;
}
