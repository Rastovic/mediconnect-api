package com.mediconnect.controller;

import com.mediconnect.dto.PatientDto;
import com.mediconnect.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("@authz.canViewPatientChart(authentication,#userId)")
    public ResponseEntity<PatientDto> getByUserId(@PathVariable Long userId) {
        return patientService.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // [A01] No role check — any authenticated user can fetch any patient profile
    //        by using their own JWT (service resolves from JWT username).
    @GetMapping("/profile")
    public ResponseEntity<PatientDto> getMyProfile() {
        return ResponseEntity.ok(patientService.getMyProfile());
    }

    // [A01] No role check — any authenticated caller can update patient profile fields.
    @PutMapping("/profile")
    public ResponseEntity<PatientDto> updateMyProfile(@RequestBody PatientDto dto) {
        return ResponseEntity.ok(patientService.updateMyProfile(dto));
    }
}
