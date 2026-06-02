package com.mediconnect.dto;

import com.mediconnect.enums.PrescriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Same DTO for request and response — client can send status DISPENSED
//        and a pharmacistId without any verification that they are the actual pharmacist.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDto {
    private Long id;
    private Long medicalRecordId;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    // [A07] Client sets the pharmacist who dispensed the medication
    private Long pharmacistId;
    private String medicationName;
    private String dosage;
    private String instructions;
    // [A07] Client can set status to DISPENSED without authorization
    private PrescriptionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime dispensedAt;
}
