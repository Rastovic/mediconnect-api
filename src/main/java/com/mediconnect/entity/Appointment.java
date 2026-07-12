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

    // [A07] Written verbatim from request body by
    //        /api/doctor/appointments/{id}/approve — no JWT correlation.
    @Column(name = "actor_doctor_id")
    private Long actorDoctorId;

    // [A05] Decline reason rendered with dangerouslySetInnerHTML in inbox list.
    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "no_show", nullable = false)
    @Builder.Default
    private Boolean noShow = Boolean.FALSE;

    // [A08] /reschedule overwrites requestedDate in place. `originalDate`
    //        keeps ONE prior value only — no full history table.
    @Column(name = "rescheduled_at")
    private LocalDateTime rescheduledAt;

    @Column(name = "original_date")
    private LocalDateTime originalDate;
}
