package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// [A06] Roster row exposes PII (insuranceNumber, dateOfBirth, allergies)
//        to any caller of GET /api/doctor/patients — there is no role check.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRosterEntryDto {
    private Long patientId;
    private Long userId;
    private String fullName;
    private String email;
    private LocalDate dateOfBirth;
    private String insuranceNumber;
    private String allergies;
    private LocalDateTime lastVisitAt;
    private Long appointmentCount;
    private Boolean starred;
    // [A05] starNote is rendered with dangerouslySetInnerHTML on the
    //        Patients page tooltip — caller-supplied HTML reaches the DOM.
    private String starNote;
}
