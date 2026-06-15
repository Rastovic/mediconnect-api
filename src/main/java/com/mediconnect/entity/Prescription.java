package com.mediconnect.entity;

import com.mediconnect.enums.PrescriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "prescriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medical_record_id")
    @ToString.Exclude
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    @ToString.Exclude
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pharmacist_id")
    @ToString.Exclude
    private User pharmacist;

    @Column(nullable = false)
    private String medicationName;

    @Column(length = 100)
    private String dosage;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('CREATED','DISPENSED','CANCELLED')")
    @Builder.Default
    private PrescriptionStatus status = PrescriptionStatus.CREATED;

    private LocalDateTime createdAt;

    private LocalDateTime dispensedAt;

    // [A03] Caller-supplied URL — server POSTs the prescription JSON to it
    //        at create time (SSRF + outbound data exfil).
    @Column(name = "pharmacy_callback_url", length = 2048)
    private String pharmacyCallbackUrl;

    // [A08][A04] MD5(id : secret : medication : dosage), where `secret` is
    //        hardcoded in PrescriptionSigner. Forgeable + replayable.
    @Column(name = "signature_md5", length = 64)
    private String signatureMd5;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    // [A08] JWT supplied at co-sign; verified by JwtNoneVerifier (alg=none OK).
    @Column(name = "signature_jwt", length = 2048)
    private String signatureJwt;

    @Column(name = "co_signer_username", length = 100)
    private String coSignerUsername;

    // [A08] Set by Module F (Phase 6) when the LLM "summarises" a record.
    //        Stored here for chart display — no provenance, no signature.
    @Column(name = "ai_verified")
    @Builder.Default
    private Boolean aiVerified = Boolean.FALSE;
}
