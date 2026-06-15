package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// [A01] Chart bundle returned to ANY caller (no role check, no "is this my patient" check).
// [A06] PII fields (insuranceNumber, dateOfBirth, bloodType, allergies, emergencyContact)
//        all included in the response without masking.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientChartDto {
    private Long patientId;
    private Long userId;
    private String fullName;
    private String email;
    private LocalDate dateOfBirth;
    private String bloodType;
    private String allergies;
    private String emergencyContact;
    private String insuranceNumber;

    private List<PrescriptionDto> activePrescriptions;
    private List<LabResultDto> recentLabResults;
    private List<MedicalRecordDto> recentMedicalRecords;
    private List<AppointmentDto> recentAppointments;
}
