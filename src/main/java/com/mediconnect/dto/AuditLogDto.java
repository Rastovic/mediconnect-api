package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Isti DTO za request i response — audit log kreiran od strane klijenta
//        može imati lažirani userId, ipAddress i action.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    // [A07] Klijent može lažirati userId u audit logu
    private Long userId;
    private String action;
    private String entityType;
    private Long entityId;
    // [A07] IP adresa uzeta iz request-a, može biti spoofovana
    private String ipAddress;
    private String userAgent;
    private String details;
    private LocalDateTime createdAt;
}
