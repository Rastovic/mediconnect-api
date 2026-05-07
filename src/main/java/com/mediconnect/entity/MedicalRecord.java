package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A08] Software and Data Integrity Failures: no contentHash field.
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

    // [A08] Stored without a content_hash — file tampering is undetectable
    @Column(length = 500)
    private String attachmentPath;

    private LocalDateTime createdAt;
}
