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
}
