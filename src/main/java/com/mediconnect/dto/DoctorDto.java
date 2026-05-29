package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A07] Same DTO for request and response.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String specialty;
    private String licenseNumber;
    private String hospital;
    private String phone;
    private String bio;
}
