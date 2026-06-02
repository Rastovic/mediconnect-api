package com.mediconnect.service;

import com.mediconnect.dto.UserDto;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordUtils passwordUtils;

    // [A01] No access control — any caller gets full list of all users
    public List<UserDto> findAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // [A01] IDOR — caller identity is never verified against the requested id
    public UserDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toDto(user);
    }

    // [A01] IDOR — no check that the authenticated caller is the user being updated.
    //        Any user can update any other user's profile by guessing their id.
    public UserDto update(Long id, UserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName()  != null) user.setLastName(dto.getLastName());
        if (dto.getPhone()     != null) user.setPhone(dto.getPhone());
        return toDto(userRepository.save(user));
    }

    // [A02] No current-password check — caller can change any user's password
    //        by supplying only the new password. [A01] No ownership check.
    public UserDto changePassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("newPassword must not be blank");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        // [A02] MD5 without salt — same weak hashing as registration
        user.setPasswordHash(passwordUtils.hashPassword(newPassword));
        return toDto(userRepository.save(user));
    }

    // [A01] Mass Assignment — role String taken directly from caller, no whitelist check
    public UserDto updateRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        // [A01] No validation: caller can pass "ADMIN" and escalate privileges
        user.setRole(Role.valueOf(role));
        return toDto(userRepository.save(user));
    }

    public void deleteById(Long id) {
        userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        userRepository.deleteById(id);
    }

    // [A04] passwordHash mapped into response DTO — no @JsonIgnore, no masking
    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .passwordHash(user.getPasswordHash())   // [A04] exposed
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .build();
    }
}
