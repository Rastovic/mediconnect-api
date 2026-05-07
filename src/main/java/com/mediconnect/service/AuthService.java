package com.mediconnect.service;

import com.mediconnect.dto.LoginRequest;
import com.mediconnect.dto.RegisterRequest;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.JwtUtil;
import com.mediconnect.security.PasswordUtils;
import com.mediconnect.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordUtils passwordUtils;
    private final JwtUtil jwtUtil;

    // [A07] Mass Assignment: role taken directly from request body
    //        without any server-side validation or whitelist check.
    //        Client can send {"role":"ADMIN"} and register an admin account.
    // [A07] No password complexity validation: length, characters, entropy.
    // [A04] MD5 without salt — delegated to PasswordUtils.hashPassword()
    public String register(RegisterRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordUtils.hashPassword(request.getPassword()))
                .role(Role.valueOf(request.getRole()))   // [A07] taken directly from client input
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        // [A04] Token returned to caller — will be sent in response body, not in HttpOnly cookie
        return jwtUtil.generateToken(new UserPrincipal(user));
    }

    // [A07] User Enumeration: three semantically distinct error messages reveal
    //        the account state to an attacker and enable username harvesting.
    // [A07] No rate limiting — unlimited attempts (brute-force / credential stuffing).
    public String login(LoginRequest request) {

        // Message 1 — explicitly confirms that the username does NOT exist in the system
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + request.getUsername()));

        // Message 2 — confirms that the user EXISTS but the password is wrong
        if (!passwordUtils.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        // Message 3 — confirms account EXISTS, password is CORRECT,
        //              but account is locked and reveals the EXACT unlock time
        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Account is locked until " + user.getLockedUntil());
        }

        // [A04] Token returned to caller — will be sent in response body
        return jwtUtil.generateToken(new UserPrincipal(user));
    }
}
