package com.mediconnect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    // Optional — if absent, username is derived from email by AuthService.
    @Size(min = 3, max = 40)
    private String username;

    private String firstName;
    private String lastName;

    @NotBlank
    @Email
    private String email;

    // Min 12 chars with upper, lower and a digit.
    @NotBlank
    @Size(min = 12)
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$",
             message = "must contain upper, lower and a digit")
    private String password;

    // NOTE: no `role` field. Registration always creates a PATIENT account;
    // the client cannot choose its own role (mass-assignment fix).
}
