package com.mediconnect.dto;

import com.mediconnect.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Same DTO for request and response — client can send a status value and
//        change the appointment state (e.g. APPROVED) without server-side authorization.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDto {
    private Long id;
    private Long patientId;
    private Long doctorId;
    // Populated on responses; ignored on requests
    private String patientName;
    private String doctorName;
    // [A07] Client can set status to APPROVED or COMPLETED directly
    private AppointmentStatus status;
    private LocalDateTime requestedDate;
    // Alias for requestedDate — matches the field name the frontend expects
    private LocalDateTime scheduledAt;
    private String notes;
    private LocalDateTime createdAt;
}
