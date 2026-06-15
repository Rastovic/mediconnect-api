package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A08] Software or Data Integrity Failures.
//        `bundle_payload` is a base64-encoded Java ObjectOutputStream blob.
//        DoctorReferralService.inbox() and accept() pass the decoded bytes
//        to ObjectInputStream.readObject() with no class allow-list, no
//        ObjectInputFilter — textbook deserialisation RCE.
@Entity
@Table(name = "referrals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "from_doctor_id")
    @ToString.Exclude
    private Doctor fromDoctor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "to_doctor_id")
    @ToString.Exclude
    private Doctor toDoctor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id")
    @ToString.Exclude
    private Patient patient;

    @Column(length = 255)
    private String subject;

    // [A08] Base64-encoded Java serialised payload. NEVER do this in real code.
    @Column(name = "bundle_payload", columnDefinition = "MEDIUMTEXT")
    private String bundlePayload;

    @Column(nullable = false)
    @Builder.Default
    private Boolean accepted = Boolean.FALSE;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
