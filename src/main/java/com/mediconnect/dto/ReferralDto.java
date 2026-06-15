package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A08] Inbox response includes the deserialised bundle preview AND the
//        raw base64 payload. Renderers on the frontend drop fields like
//        `summary` directly into the DOM via dangerouslySetInnerHTML.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralDto {
    private Long id;
    private Long fromDoctorId;
    private String fromDoctorName;
    private Long toDoctorId;
    private String toDoctorName;
    private Long patientId;
    private String patientName;
    private String subject;
    // Decoded preview (best-effort).
    private String previewPatientName;
    private String previewSummary;
    private String previewDiagnosis;
    private String previewMedication;
    // Raw base64 payload preserved so caller can audit / replay.
    private String bundlePayload;
    private String decodeStatus;
    private Boolean accepted;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
}
