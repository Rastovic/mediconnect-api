package com.mediconnect.controller;

import com.mediconnect.dto.DoctorDto;
import com.mediconnect.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// [A01] No role check — any caller (unauthenticated included) can list all doctors,
//        including their license number, hospital, phone, email and user ID.
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping
    public ResponseEntity<List<DoctorDto>> listDoctors() {
        return ResponseEntity.ok(doctorService.findAll());
    }

    // [A01] No role check — any authenticated user can call this endpoint.
    @GetMapping("/profile")
    public ResponseEntity<DoctorDto> getMyProfile() {
        return ResponseEntity.ok(doctorService.getMyProfile());
    }

    // [A01] No role check — any authenticated caller can update doctor profile fields.
    @PutMapping("/profile")
    public ResponseEntity<DoctorDto> updateMyProfile(@RequestBody DoctorDto dto) {
        return ResponseEntity.ok(doctorService.updateMyProfile(dto));
    }
}
