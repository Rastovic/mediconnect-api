package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// [A07] Isti DTO za request i response — klijent može postaviti insuranceNumber
//        ili drugog korisnika kao vlasnika profila (user_id).
// [A04] Svi PII podaci vraćeni u odgovoru bez maskovanja.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {
    private Long id;
    // [A07] Klijent može proslijediti tuđi userId i preuzeti profil
    private Long userId;
    // [A04] PII plaintext u response-u
    private String insuranceNumber;
    private LocalDate dateOfBirth;
    private String bloodType;
    private String allergies;
    private String emergencyContact;
}
