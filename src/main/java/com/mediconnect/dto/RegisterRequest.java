package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// [A07] Mass Assignment: 'role' field accepts a value directly from the client.
//        No @Valid or any validation annotation — length, format,
//        and password complexity are not checked.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    // Optional — if absent, username is derived from email by AuthService
    private String username;

    // [A07] Accepted by the deserializer but never stored — silently ignored by AuthService.
    //        Demonstrates mass assignment: extra fields in the request body raise no error.
    private String firstName;
    private String lastName;

    private String email;

    // [A07] No @Size, @Pattern or @NotBlank — "1" is accepted as a valid password
    private String password;

    // [A07] Client chooses their own role — can send "ADMIN" to gain admin privileges
    private String role;
}
