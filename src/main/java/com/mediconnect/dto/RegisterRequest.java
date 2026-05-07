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

    private String username;

    private String email;

    // [A07] No @Size, @Pattern or @NotBlank — "1" is accepted as a valid password
    private String password;

    // [A07] Client chooses their own role — can send "ADMIN" to gain admin privileges
    private String role;
}
