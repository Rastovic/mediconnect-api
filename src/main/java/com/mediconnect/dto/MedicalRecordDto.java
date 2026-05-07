package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Isti DTO za request i response — klijent može slobodno postaviti patientId,
//        doctorId i attachmentPath (path traversal vektor).
// [A08] Nema contentHash polja — integritet attachment-a nije provjerliv.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordDto {
    private Long id;
    private Long patientId;
    private Long doctorId;
    private Long appointmentId;
    private String diagnosis;
    private String prescription;
    // [A07+A08] Klijent kontroliše putanju fajla, nema hash provjere
    private String attachmentPath;
    private LocalDateTime createdAt;
}
