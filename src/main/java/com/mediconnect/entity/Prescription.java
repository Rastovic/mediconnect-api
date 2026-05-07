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
    @JoinColumn(name = "medical_record_id", nullable = false)
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
}
