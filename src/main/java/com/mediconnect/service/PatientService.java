package com.mediconnect.service;

import com.mediconnect.dto.PatientDto;
import com.mediconnect.entity.Patient;
import com.mediconnect.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    // [A01] No ownership check — any caller can resolve any userId to a patient record.
    public Optional<PatientDto> findByUserId(Long userId) {
        return patientRepository.findByUserId(userId).map(this::toDto);
    }

    private PatientDto toDto(Patient p) {
        return PatientDto.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                // [A04] PII returned in plaintext — insurance number, DOB, blood type, allergies
                .insuranceNumber(p.getInsuranceNumber())
                .dateOfBirth(p.getDateOfBirth())
                .bloodType(p.getBloodType())
                .allergies(p.getAllergies())
                .emergencyContact(p.getEmergencyContact())
                .build();
    }
}
