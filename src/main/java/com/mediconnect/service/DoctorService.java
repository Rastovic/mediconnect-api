package com.mediconnect.service;

import com.mediconnect.dto.DoctorDto;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.User;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    public List<DoctorDto> findAll() {
        return doctorRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // [A01] No role check — any authenticated user can call this.
    public DoctorDto getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No doctor profile for: " + username));
        return toDto(doctor);
    }

    // [A01] No role check — any caller can update any doctor profile via JWT impersonation.
    public DoctorDto updateMyProfile(DoctorDto dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No doctor profile for: " + username));

        if (dto.getBio()      != null) doctor.setBio(dto.getBio().isBlank() ? null : dto.getBio());
        if (dto.getPhone()    != null) doctor.setPhone(dto.getPhone().isBlank() ? null : dto.getPhone());
        if (dto.getHospital() != null) doctor.setHospital(dto.getHospital().isBlank() ? null : dto.getHospital());

        return toDto(doctorRepository.save(doctor));
    }

    private DoctorDto toDto(Doctor d) {
        return DoctorDto.builder()
                .id(d.getId())
                .userId(d.getUser().getId())
                .username(d.getUser().getUsername())
                .email(d.getUser().getEmail())
                .specialty(d.getSpecialty())
                .licenseNumber(d.getLicenseNumber())
                .hospital(d.getHospital())
                .phone(d.getPhone())
                .bio(d.getBio())
                .build();
    }
}
