package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A05] stored_filename is the verbatim `getOriginalFilename()` from the
//        upload — path traversal vector at write time.
// [A02] content_type is whatever the upload header said it was; the
//        GET endpoint echoes it back so `image/svg+xml` becomes a stored
//        XSS surface for any viewer.
@Entity
@Table(name = "imaging_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ImagingFile {

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

    @Column(name = "stored_filename", nullable = false, length = 500)
    private String storedFilename;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    // [A03] Populated when the image was fetched via /imaging/import-url.
    //        Caller-supplied URL — SSRF target preserved for history.
    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
