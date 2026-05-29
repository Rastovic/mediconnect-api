package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    // [A07] Both username and email accepted — service tries email first, then username.
    // Frontend sends email; legacy clients may still send username.
    private String username;
    private String email;
    private String password;
}
