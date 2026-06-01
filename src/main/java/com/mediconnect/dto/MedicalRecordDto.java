package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Same DTO for request and response — client can freely set patientId,
//        doctorId and attachmentPath (path traversal vector).
// [A08] No contentHash field — attachment integrity cannot be verified.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordDto {
    private Long id;
    private Long patientId;
    private Long doctorId;
    private Long appointmentId;
    // Populated on responses; resolved server-side
    private String patientName;
    private String doctorName;
    private String diagnosis;
    private String prescription;
    // Frontend uses 'notes'; mapped to/from prescription internally
    private String notes;
    // [A07+A08] Client controls the file path — no hash verification
    private String attachmentPath;
    private LocalDateTime createdAt;
}
