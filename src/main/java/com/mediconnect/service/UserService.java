package com.mediconnect.service;

import com.mediconnect.dto.UserDto;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
                .passwordHash(user.getPasswordHash())   // [A04] exposed
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .build();
    }
}
