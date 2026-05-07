package com.mediconnect.entity;

import com.mediconnect.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [A04] Nema @JsonIgnore ni na jednom polju — passwordHash, role, active,
//        failedLoginAttempts i lockedUntil biće vidljivi u svakom API odgovoru.
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

    // [A04] Vidljivo u API odgovoru — klijent dobiva hash lozinke
    @Column(nullable = false)
    private String passwordHash;

    // [A04] Vidljivo u API odgovoru — klijent vidi i može predložiti svoju rolu
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PATIENT','DOCTOR','LAB_TECH','PHARMACIST','ADMIN')")
    private Role role;

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime createdAt;

    // [A04] Vidljivo — napadač zna koliko pokušaja je preostalo
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    // [A04] Vidljivo — napadač zna tačno do kada je nalog zaključan
    private LocalDateTime lockedUntil;
}
