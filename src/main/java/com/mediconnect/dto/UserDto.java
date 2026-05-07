package com.mediconnect.dto;

import com.mediconnect.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Mass Assignment: isti DTO za request i response.
//        Klijent može poslati passwordHash, role, active, failedLoginAttempts
//        i direktno uticati na privilegije i stanje naloga.
// [A04] passwordHash vraćen u response-u — bez @JsonIgnore.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    // [A04] Hash lozinke vidljiv u API odgovoru
    private String passwordHash;
    // [A07] Klijent može poslati "role": "ADMIN" pri registraciji ili update-u
    private Role role;
    // [A07] Klijent može poslati "active": true da reaktivira banovan nalog
    private Boolean active;
    private LocalDateTime createdAt;
    // [A07] Klijent može resetovati brojač neuspješnih pokušaja
    private Integer failedLoginAttempts;
    // [A07] Klijent može otključati nalog postavljanjem null
    private LocalDateTime lockedUntil;
}
