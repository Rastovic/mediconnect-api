package com.mediconnect.entity;

import com.mediconnect.enums.LabResultStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    @ToString.Exclude
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_tech_id", nullable = false)
    @ToString.Exclude
    private User labTech;

    @Column(nullable = false)
    private String testName;

    @Column(columnDefinition = "TEXT")
    private String resultValue;

    @Column(length = 50)
    private String unit;

    @Column(length = 100)
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PENDING','COMPLETED','CANCELLED')")
    @Builder.Default
    private LabResultStatus status = LabResultStatus.PENDING;

    private LocalDateTime testDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 500)
    private String attachmentPath;

    @Column(length = 64)
    private String contentHash;
}
