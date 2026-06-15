package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

// [A05] renderedHtml is rendered with dangerouslySetInnerHTML on the Doctor
//        Note detail page — any SSTI payload accepted at create time reaches
//        the DOM here.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalNoteDto {
    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    // [A03] Caller-controlled — see DoctorNoteService#renderTemplate.
    private String templateName;
    private Map<String, Object> data;
    private String rawData;
    private String renderedHtml;
    // [A08] Signature accepted as JWT alg=none; signer trusted from `sub`.
    private String signature;
    private String signerUsername;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
