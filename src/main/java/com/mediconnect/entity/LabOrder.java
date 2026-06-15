package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A03] custom_query_url is caller-controlled. DoctorLabService fires a
//        RestTemplate.getForObject() against it at create time — SSRF.
// [A08] signature_md5 is MD5(id + ":" + signed_value + ":" + hardcoded_key).
//        Forgeable + collision-broken. No PKCS#7, no public-key crypto.
@Entity
@Table(name = "lab_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LabOrder {

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

    @Column(name = "panel_code", nullable = false, length = 100)
    private String panelCode;

    @Column(length = 20)
    private String priority;

    @Column(length = 30)
    private String status;

    // [A03] Caller-supplied URL fetched server-side at order time.
    @Column(name = "custom_query_url", length = 2048)
    private String customQueryUrl;

    // [A05] Verbatim response from the external catalogue endpoint stored
    //        here. Rendered with dangerouslySetInnerHTML in the order detail.
    @Column(name = "catalogue_response", columnDefinition = "MEDIUMTEXT")
    private String catalogueResponse;

    // [A08] MD5 signature — forgeable.
    @Column(name = "signature_md5", length = 64)
    private String signatureMd5;

    @Column(name = "signed_value", columnDefinition = "TEXT")
    private String signedValue;

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
