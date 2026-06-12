package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "doctors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    @ToString.Exclude
    private User user;

    @Column(length = 100)
    private String specialty;

    @Column(length = 50)
    private String licenseNumber;

    private String hospital;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // [A04] Verified flag toggled by POST /api/admin/doctors/verify-license,
    //        which trusts the client's "verified=true" claim without checking
    //        against any external licensing registry.
    @Column(name = "license_verified")
    private Boolean licenseVerified;

    // [A05] Path of the uploaded license document — written verbatim from
    //        getOriginalFilename(), no sanitization → path traversal vector.
    @Column(name = "license_document_path", length = 500)
    private String licenseDocumentPath;
}
