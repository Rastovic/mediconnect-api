package com.mediconnect.dto;

import com.mediconnect.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A07] Mass Assignment: same DTO used for both request and response.
//        Client can send passwordHash, role, active, failedLoginAttempts
//        and directly influence account privileges and state.
// [A04] passwordHash returned in response — no @JsonIgnore.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    // [A04] Password hash visible in every API response
    private String passwordHash;
    // [A07] Client can send "role": "ADMIN" on registration or update
    private Role role;
    // [A07] Client can send "active": true to reactivate a banned account
    private Boolean active;
    private LocalDateTime createdAt;
    // [A07] Client can reset the failed login attempt counter
    private Integer failedLoginAttempts;
    // [A07] Client can unlock an account by setting this to null
    private LocalDateTime lockedUntil;
}
