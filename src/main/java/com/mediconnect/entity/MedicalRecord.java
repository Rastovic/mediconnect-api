package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A08] Software or Data Integrity Failures: no contentHash field.
//        Tampering with attachment files cannot be detected, and the integrity
//        of diagnoses and prescriptions cannot be verified.
@Entity
@Table(name = "medical_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    @ToString.Exclude
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "appointment_id")
    @ToString.Exclude
    private Appointment appointment;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String prescription;

    @Column(length = 500)
    private String attachmentPath;

    // SHA-256 of the stored attachment — lets a download be integrity-checked.
    @Column(length = 64)
    private String contentHash;

    private LocalDateTime createdAt;

    // [A08] Set by DoctorAIService when an external LLM "summarises" a record.
    //        Frontend chart renders a green "AI verified" check whenever this
    //        is true — no provenance, no signature, no countersignature.
    @Column(name = "ai_verified")
    @Builder.Default
    private Boolean aiVerified = Boolean.FALSE;

    // [A03] Caller-supplied modelUrl preserved so the SSRF target is visible
    //        in the chart row when reviewing the AI-verified record.
    @Column(name = "ai_model_url", length = 2048)
    private String aiModelUrl;
}
