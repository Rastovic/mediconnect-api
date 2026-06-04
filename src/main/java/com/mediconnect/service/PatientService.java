package com.mediconnect.service;

import com.mediconnect.dto.PatientDto;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.User;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    // [A01] No ownership check — any caller can resolve any userId to a patient record.
    public Optional<PatientDto> findByUserId(Long userId) {
        return patientRepository.findByUserId(userId).map(this::toDto);
    }

    // [A01] Returns the profile of the currently authenticated patient.
    //        No check that the user actually holds the PATIENT role.
    public PatientDto getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Patient patient = patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No patient profile for: " + username));
        return toDto(patient);
    }

    // [A01] No role check — any authenticated user can call this endpoint.
    public PatientDto updateMyProfile(PatientDto dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        Patient patient = patientRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No patient profile for: " + username));

        if (dto.getBloodType()        != null) patient.setBloodType(dto.getBloodType().isBlank() ? null : dto.getBloodType());
        if (dto.getDateOfBirth()      != null) patient.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getAllergies()         != null) patient.setAllergies(dto.getAllergies().isBlank() ? null : dto.getAllergies());
        if (dto.getInsuranceNumber()  != null) patient.setInsuranceNumber(dto.getInsuranceNumber().isBlank() ? null : dto.getInsuranceNumber());
        if (dto.getEmergencyContact() != null) patient.setEmergencyContact(dto.getEmergencyContact().isBlank() ? null : dto.getEmergencyContact());

        return toDto(patientRepository.save(patient));
    }

    private PatientDto toDto(Patient p) {
        return PatientDto.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                // [A06] PII returned in plaintext — insurance number, DOB, blood type, allergies
                .insuranceNumber(p.getInsuranceNumber())
                .dateOfBirth(p.getDateOfBirth())
                .bloodType(p.getBloodType())
                .allergies(p.getAllergies())
                .emergencyContact(p.getEmergencyContact())
                .build();
    }
}
