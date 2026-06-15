package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A02] join_token spliced into room_url as ?token=<plaintext> — leaks via
//        Referer, browser history, screenshots, server access logs.
// [A02] reason_for_visit ends up in the public iCal feed SUMMARY (PHI).
@Entity
@Table(name = "telemedicine_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TelemedicineSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id")
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    @ToString.Exclude
    private Doctor doctor;

    @Column(name = "room_url", nullable = false, length = 1024)
    private String roomUrl;

    @Column(name = "join_token", nullable = false, length = 128)
    private String joinToken;

    @Column(name = "reason_for_visit", length = 500)
    private String reasonForVisit;

    @Column(length = 30)
    private String status;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // [A03] Caller-supplied recording URL — fetched server-side at attach time.
    @Column(name = "recording_url", length = 2048)
    private String recordingUrl;

    // [A02] basename of recordingUrl used as filename — path traversal vector.
    @Column(name = "recording_path", length = 1000)
    private String recordingPath;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
