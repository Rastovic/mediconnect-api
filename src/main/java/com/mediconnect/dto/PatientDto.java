package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// [A07] Same DTO for request and response — client can supply another user's userId
//        and take ownership of a different patient profile.
// [A04] All PII fields returned in the response without masking.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {
    private Long id;
    // [A07] Client can supply another user's userId to hijack their profile
    private Long userId;
    // [A04] PII in plaintext in the response
    private String insuranceNumber;
    private LocalDate dateOfBirth;
    private String bloodType;
    private String allergies;
    private String emergencyContact;
}
