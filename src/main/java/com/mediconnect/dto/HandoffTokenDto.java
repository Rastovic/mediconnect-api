package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A08] Handoff token is HMAC-SHA1 signed with a hardcoded key and carries
//        no expiry — any caller who learns the URL can hand off ANY patient
//        to ANY doctor indefinitely.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffTokenDto {
    private Long patientId;
    private String token;
    private String url;
}
