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
    public User register(RegisterRequest request) {
        // [A07] User Enumeration: explicit "username already taken" message reveals
        //        which usernames are registered, enabling account harvesting.
        String username = (request.getUsername() != null && !request.getUsername().isBlank())
                ? request.getUsername()
                : request.getEmail().split("@")[0];

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken: " + username);
        }

        // [A07] User Enumeration: also confirms which emails are registered
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        String roleStr = (request.getRole() != null && !request.getRole().isBlank())
                ? request.getRole()
                : "PATIENT";

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordUtils.hashPassword(request.getPassword()))
                .role(Role.valueOf(roleStr))   // [A07] taken directly from client input
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    // [A07] User Enumeration: three semantically distinct error messages reveal
    //        the account state to an attacker and enable username harvesting.
    // [A07] No rate limiting — unlimited attempts (brute-force / credential stuffing).
    public User login(LoginRequest request) {

        // Prefer email lookup (frontend sends email); fall back to username for legacy clients
        User user;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            // Message 1 variant — confirms the email does NOT exist
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() ->
                            new RuntimeException("User not found: " + request.getEmail()));
        } else {
            // Message 1 — explicitly confirms that the username does NOT exist in the system
            user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() ->
                            new RuntimeException("User not found: " + request.getUsername()));
        }

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

        return user;
    }
}
