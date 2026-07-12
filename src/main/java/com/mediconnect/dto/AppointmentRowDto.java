package com.mediconnect.dto;

import com.mediconnect.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A05] declineReason rendered via dangerouslySetInnerHTML on the doctor
//        appointments inbox.
// [A07] actorDoctorId carried in clear — caller-written attribution.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRowDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private AppointmentStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime originalDate;
    private LocalDateTime rescheduledAt;
    private String notes;
    private String declineReason;
    private Long actorDoctorId;
    private Boolean noShow;
    private LocalDateTime createdAt;
}
