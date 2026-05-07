package com.mediconnect.dto;

import com.mediconnect.enums.LabResultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Isti DTO za request i response — klijent može postaviti status COMPLETED
//        i attachmentPath bez laboratorijske verifikacije.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabResultDto {
    private Long id;
    private Long patientId;
    private Long labTechId;
    private String testName;
    private String resultValue;
    private String unit;
    private String referenceRange;
    // [A07] Klijent može promijeniti status laboratorijskog nalaza
    private LabResultStatus status;
    private LocalDateTime testDate;
    private String notes;
    private String attachmentPath;
}
