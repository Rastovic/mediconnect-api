package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// [A04] PII podaci (insurance_number, date_of_birth, blood_type, allergies,
//        emergency_contact) vidljivi u API odgovoru bez maskovanja.
@Entity
@Table(name = "patients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    @ToString.Exclude
    private User user;

    private String insuranceNumber;

    private LocalDate dateOfBirth;

    @Column(length = 5)
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    private String emergencyContact;
}
