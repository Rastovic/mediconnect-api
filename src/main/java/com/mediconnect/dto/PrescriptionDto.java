package com.mediconnect.dto;

import com.mediconnect.enums.PrescriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Isti DTO za request i response — klijent može poslati status DISPENSED
//        i pharmacistId bez stvarne provjere da li je farmaceut taj koji šalje zahtjev.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDto {
    private Long id;
    private Long medicalRecordId;
    private Long patientId;
    private Long doctorId;
    // [A07] Klijent postavlja farmaceuta koji je izdao lijek
    private Long pharmacistId;
    private String medicationName;
    private String dosage;
    private String instructions;
    // [A07] Klijent može postaviti status na DISPENSED bez autorizacije
    private PrescriptionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime dispensedAt;
}
