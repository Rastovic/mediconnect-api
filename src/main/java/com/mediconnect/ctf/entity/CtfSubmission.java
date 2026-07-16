package com.mediconnect.ctf.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Full attempt log (plan §5.2) - also a positive A09 counterpoint. */
@Entity
@Table(name = "ctf_submission")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CtfSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(name = "submitted_flag_hash", length = 64)
    private String submittedFlagHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean correct = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
