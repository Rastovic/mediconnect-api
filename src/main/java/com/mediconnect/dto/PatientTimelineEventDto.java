package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A09] Timeline entries are sourced directly from AuditLog rows. The `details`
//        field of an audit row holds the raw HTTP request body (passwords, JWTs,
//        free-text complaints). Surfacing it to any caller of the timeline
//        endpoint exposes those secrets to a different principal than the one
//        who originally submitted them.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientTimelineEventDto {
    private Long id;
    private String type;
    private String summary;
    private String rawDetails;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime occurredAt;
}
