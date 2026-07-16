package com.mediconnect.ctf.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single graded CTF challenge. This is CTF instrumentation, deliberately
 * hardened (unlike the app under test): flagHash/flagSalt are never exposed
 * through a DTO. See CTF_PLATFORM_PLAN.md §5.
 */
@Entity
@Table(name = "ctf_challenge")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CtfChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "owasp_category", nullable = false, length = 4)
    private String owaspCategory;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "finding_id")
    private Integer findingId;

    @Column(length = 512)
    private String summary;

    @Column(length = 512)
    private String objective;

    @Column(name = "target_hint", length = 512)
    private String targetHint;

    @Column(name = "intended_path", columnDefinition = "TEXT")
    private String intendedPath;

    /** Salted sha256 of the flag. Never serialised to a client. */
    @Column(name = "flag_hash", nullable = false, length = 64)
    private String flagHash;

    @Column(name = "flag_salt", nullable = false, length = 32)
    private String flagSalt;

    @Column(name = "is_behavioral", nullable = false)
    @Builder.Default
    private Boolean behavioral = false;

    @Column(name = "is_core", nullable = false)
    @Builder.Default
    private Boolean core = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
