package com.mediconnect.service;

import com.mediconnect.dto.DoctorDto;
import com.mediconnect.entity.Doctor;
import com.mediconnect.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public List<DoctorDto> findAll() {
        return doctorRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
