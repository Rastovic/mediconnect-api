package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A07] Isti DTO za request i response.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {
    private Long id;
    private Long userId;
    private String specialty;
    private String licenseNumber;
    private String hospital;
    private String phone;
    private String bio;
}
