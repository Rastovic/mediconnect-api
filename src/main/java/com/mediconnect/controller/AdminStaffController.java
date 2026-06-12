package com.mediconnect.controller;

import com.mediconnect.service.AdminStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Module B — Clinical Staff Onboarding
//
// [A01] All endpoints under permitAll(). No role check.
//
//   GET    /doctors                    — admin view of doctor profiles
//   PUT    /doctors/{id}               — mass-assign doctor fields (A07)
//   POST   /doctors/{id}/verify-license — flip licenseVerified flag (A04)
//   POST   /staff/onboard              — multipart create user + role profile + upload (A05 + A07)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStaffController {

    private final AdminStaffService adminStaffService;

    @GetMapping("/doctors")
    public ResponseEntity<List<Map<String, Object>>> listDoctors() {
        return ResponseEntity.ok(adminStaffService.listDoctors());
    }

    @PutMapping("/doctors/{id}")
    public ResponseEntity<Map<String, Object>> updateDoctor(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminStaffService.updateDoctor(id, body));
    }

    // [A04] Verification is a single boolean flip — caller is the source of truth.
    @PostMapping("/doctors/{id}/verify-license")
    public ResponseEntity<Map<String, Object>> verifyLicense(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminStaffService.verifyLicense(id, body == null ? Map.of() : body));
    }

    // [A05][A07] Multipart endpoint — creates a User (any role) and writes
    //              the uploaded license document via the path-traversal sink.
    @PostMapping(value = "/staff/onboard", consumes = { "multipart/form-data" })
    public ResponseEntity<Map<String, Object>> onboardStaff(
            @RequestParam Map<String, String> formFields,
            @RequestPart(value = "licenseDocument", required = false) MultipartFile licenseDocument
    ) throws IOException {
        // Copy into a mutable map (Spring's @RequestParam Map is immutable singleton-list values
        // wrapper in some configs)
        Map<String, String> fields = new HashMap<>(formFields);
        return ResponseEntity.status(201).body(adminStaffService.onboardStaff(fields, licenseDocument));
    }
}
