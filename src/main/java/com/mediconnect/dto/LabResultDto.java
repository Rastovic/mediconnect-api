package com.mediconnect.dto;

import com.mediconnect.enums.LabResultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Same DTO for request and response — client can set status to COMPLETED
//        and supply an attachmentPath without any lab verification.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabResultDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long labTechId;
    private String testName;
    private String resultValue;
    private String unit;
    private String referenceRange;
    // [A07] Client can change the lab result status directly
    private LabResultStatus status;
    // Field name matches frontend interface (resultDate)
    private LocalDateTime resultDate;
    private String notes;
    private String attachmentPath;
}
