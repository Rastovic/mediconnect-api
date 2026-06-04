package com.mediconnect.entity;

import com.mediconnect.enums.RefillStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A10] Mishandling of Exceptional Conditions — entity backing the Async Refill Queue.
//        Several fields exist specifically to host A10 vulnerabilities:
//          - `quantity` is nullable → CWE-754 (validator NPE → fail-open).
//          - `failureReason` stores raw exception text → CWE-209 leak.
//          - `tempSlipPath` stores absolute FS path → CWE-209 / A09 leak.
//          - `retryCount` has no max-retry guard → CWE-400.
//          - No @Version column → CWE-362 (TOCTOU double-dispense).
@Entity
@Table(name = "refill_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RefillRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    // [A07] caller-supplied actor — never re-checked against the JWT
    @Column(name = "requested_by")
    private Long requestedBy;

    // [A10] CWE-754 — nullable; the validator dereferences it without a null check
    @Column(name = "quantity")
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefillStatus status = RefillStatus.REQUESTED;

    // [A10][A09] raw exception class + message stored here; returned verbatim
    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    // [A10] CWE-400 — no maximum retry value; unbounded retries amplify failures
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "dispensed_at")
    private LocalDateTime dispensedAt;

    @Column(name = "pharmacist_id")
    private Long pharmacistId;

    // [A10][A09] absolute filesystem path of the printable slip; leaked via DTO
    @Column(name = "temp_slip_path", length = 512)
    private String tempSlipPath;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = RefillStatus.REQUESTED;
        if (retryCount == null) retryCount = 0;
    }
}
