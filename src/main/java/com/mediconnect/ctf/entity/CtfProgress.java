package com.mediconnect.ctf.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-user solved state. Only the "solved" fact is persisted; LOCKED/OPEN is
 * derived at read time from ctf_settings, never stored (plan §5.2).
 */
@Entity
@Table(name = "ctf_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_ctf_progress_user_challenge",
                columnNames = {"user_id", "challenge_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CtfProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean solved = false;

    @Column(name = "solved_at")
    private LocalDateTime solvedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "hints_used", nullable = false)
    @Builder.Default
    private Integer hintsUsed = 0;
}
