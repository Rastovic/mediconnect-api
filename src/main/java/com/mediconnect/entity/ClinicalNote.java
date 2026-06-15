package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A08] No `version` column, no previous-revision link — PUT /api/doctor/notes/{id}
//        overwrites the row in place. No `editedBy` column either, so even the
//        identity of the last edit is lost.
// [A09] No soft-delete column — DELETE removes the row outright and leaves no
//        audit trail of what was there.
@Entity
@Table(name = "clinical_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    @ToString.Exclude
    private Doctor doctor;

    // [A03] Caller-supplied template name. Resolved as
    //        `notes/templates/${templateName}.ftl` with no path normalisation
    //        and rendered through Freemarker with the raw `data` map as model.
    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    // [A05] Stored HTML — rendered with dangerouslySetInnerHTML on the
    //        Doctor Note detail page.
    @Column(name = "rendered_html", columnDefinition = "MEDIUMTEXT")
    private String renderedHtml;

    // [A08] Signature accepted as a JWT with alg=none — `sub` claim trusted
    //        verbatim; signature bytes ignored. Stored verbatim alongside the
    //        signer username so the UI can render "signed by".
    @Column(length = 2048)
    private String signature;

    @Column(name = "signer_username", length = 100)
    private String signerUsername;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
