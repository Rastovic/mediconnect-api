package com.mediconnect.service;

import com.mediconnect.dto.LoginRequest;
import com.mediconnect.dto.RegisterRequest;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.JwtUtil;
import com.mediconnect.security.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordUtils passwordUtils;
    private final JwtUtil jwtUtil;

    /**
     * Registration always creates a PATIENT. The client cannot choose its role.
     * A single generic failure message is returned regardless of which unique
     * constraint is hit, so registered accounts cannot be enumerated.
     */
    public User register(RegisterRequest request) {
        String username = (request.getUsername() != null && !request.getUsername().isBlank())
                ? request.getUsername()
                : request.getEmail().split("@")[0];

        if (userRepository.existsByUsername(username)) {
            log.info("Registration rejected: username already taken ({})", username);
            throw new RuntimeException("Registration failed");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.info("Registration rejected: email already registered ({})", request.getEmail());
            throw new RuntimeException("Registration failed");
        }

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(passwordUtils.hashPassword(request.getPassword()))
                .role(Role.PATIENT)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    /**
     * All failure branches (unknown user / wrong password / locked / inactive)
     * throw the same BadCredentialsException with a constant message, so no
     * account state leaks to the caller.
     */
    public User login(LoginRequest request) {
        User user;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user = userRepository.findByEmail(request.getEmail()).orElse(null);
        } else {
            user = userRepository.findByUsername(request.getUsername()).orElse(null);
        }

        if (user == null) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // Locked account — do not reveal the unlock time.
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!passwordUtils.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // Success: reset counters, unlock, and transparently upgrade legacy hashes.
        boolean dirty = false;
        if (user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            dirty = true;
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            dirty = true;
        }
        if (passwordUtils.isLegacyHash(user.getPasswordHash())) {
            user.setPasswordHash(passwordUtils.hashPassword(request.getPassword()));
            dirty = true;
        }
        if (dirty) {
            userRepository.save(user);
        }
        return user;
    }

    private void registerFailedAttempt(User user) {
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        }
        userRepository.save(user);
    }
}
