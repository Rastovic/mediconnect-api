package com.mediconnect.dto;

import com.mediconnect.entity.RefillRequest;
import com.mediconnect.enums.RefillStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A10][A09] Response DTO exposes failureReason and tempSlipPath verbatim,
//        leaking exception text (CWE-209) and internal filesystem paths to any
//        API caller and to the pharmacist UI.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefillRequestDto {
    private Long id;
    private Long prescriptionId;
    // human-readable label for the prescribed medication
    private String medicationName;
    private Long patientId;
    // patient's first + last name (falls back to username)
    private String patientName;
    // [A07] caller-supplied actor — preserved in responses for the audit demo
    private Long requestedBy;
    // resolved name of the requester (informational only — does NOT enforce auth)
    private String requestedByName;
    // [A10] nullable on purpose — triggers validator NPE downstream
    private Integer quantity;
    private RefillStatus status;
    // [A10][A09] raw java exception .toString() — DB table + constraint names leak
    private String failureReason;
    private Integer retryCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime createdAt;
    private LocalDateTime dispensedAt;
    private Long pharmacistId;
    private String pharmacistName;
    // [A10][A09] absolute filesystem path leaked to the API caller
    private String tempSlipPath;

    // Bare conversion — names left null. RefillQueueService.toDto fills them in
    //        once the lookup repositories are available.
    public static RefillRequestDto from(RefillRequest r) {
        return RefillRequestDto.builder()
                .id(r.getId())
                .prescriptionId(r.getPrescriptionId())
                .patientId(r.getPatientId())
                .requestedBy(r.getRequestedBy())
                .quantity(r.getQuantity())
                .status(r.getStatus())
                .failureReason(r.getFailureReason())
                .retryCount(r.getRetryCount())
                .lastAttemptAt(r.getLastAttemptAt())
                .createdAt(r.getCreatedAt())
                .dispensedAt(r.getDispensedAt())
                .pharmacistId(r.getPharmacistId())
                .tempSlipPath(r.getTempSlipPath())
                .build();
    }
}
