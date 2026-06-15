package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private String panelCode;
    private String priority;
    private String status;
    // [A03] Caller-supplied URL stored verbatim — used by the server at
    //        create-time as a RestTemplate target (SSRF).
    private String customQueryUrl;
    // [A05] Stored verbatim, rendered with dangerouslySetInnerHTML in the UI.
    private String catalogueResponse;
    // [A08] MD5 over (id : signedValue : "lab-sign-key-2024"). Forgeable.
    private String signatureMd5;
    private String signedValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
