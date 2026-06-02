package com.mediconnect.entity;

import com.mediconnect.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A04] No @JsonIgnore on any field — passwordHash, role, active,
//        failedLoginAttempts and lockedUntil are all exposed in every API response.
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    // [A04] Password hash returned in every API response — no @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    // [A04] Role exposed in response — client can also suggest their own role
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PATIENT','DOCTOR','LAB_TECH','PHARMACIST','ADMIN')")
    private Role role;

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime createdAt;

    // [A04] Exposed — attacker knows exactly how many attempts remain
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    // [A04] Exposed — attacker knows the exact time the account unlocks
    private LocalDateTime lockedUntil;

    @Column(length = 80)
    private String firstName;

    @Column(length = 80)
    private String lastName;

    @Column(length = 20)
    private String phone;
}
