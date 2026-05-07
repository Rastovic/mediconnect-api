package com.mediconnect.entity;

import com.mediconnect.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Appointment {

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

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('REQUESTED','APPROVED','CANCELLED','COMPLETED')")
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.REQUESTED;

    @Column(nullable = false)
    private LocalDateTime requestedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDateTime createdAt;
}
