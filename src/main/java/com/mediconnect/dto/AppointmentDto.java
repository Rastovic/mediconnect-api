package com.mediconnect.dto;

import com.mediconnect.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Isti DTO za request i response — klijent može poslati status i direktno
//        promijeniti stanje termina (npr. APPROVED) bez serverske autorizacije.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDto {
    private Long id;
    private Long patientId;
    private Long doctorId;
    // [A07] Klijent može postaviti status na APPROVED ili COMPLETED
    private AppointmentStatus status;
    private LocalDateTime requestedDate;
    private String notes;
    private LocalDateTime createdAt;
}
