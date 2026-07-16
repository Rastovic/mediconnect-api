package com.mediconnect.controller;

import com.mediconnect.dto.PrescriptionDto;
import com.mediconnect.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// [A02] PUT /{id}/dispense — no check that the prescription is not already DISPENSED.
// [A02] PUT /{id}/status  — any status transition accepted, including CANCELLED → DISPENSED.
// [A01] No role enforcement — a PATIENT can call dispense or change status.
@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    // [A01] No access control — any caller gets all prescriptions
    @GetMapping
    public ResponseEntity<List<PrescriptionDto>> getAll() {
        return ResponseEntity.ok(prescriptionService.findAll());
    }

    @PostMapping
    public ResponseEntity<PrescriptionDto> create(@RequestBody PrescriptionDto dto) {
        return ResponseEntity.status(201).body(prescriptionService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canViewPrescription(authentication,#id)")
    public ResponseEntity<PrescriptionDto> getById(@PathVariable Long id) {
        // [A01] No check that the caller is the patient or prescribing doctor
        return ResponseEntity.ok(prescriptionService.findById(id));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@authz.canViewPatientRecords(authentication,#patientId)")
    public ResponseEntity<List<PrescriptionDto>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(prescriptionService.findByPatientId(patientId));
    }

    // Dispensing is restricted to pharmacists (and admins). The state machine
    // (CREATED -> DISPENSED, once) is enforced in the service, and the dispensing
    // pharmacist is taken from the authenticated principal, not the request body.
    @PutMapping("/{id}/dispense")
    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    public ResponseEntity<PrescriptionDto> dispense(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Long> body) {
        Long pharmacistId = (body != null) ? body.get("pharmacistId") : null;
        return ResponseEntity.ok(prescriptionService.dispense(id, pharmacistId));
    }

    // Status changes are restricted to clinical/pharmacy staff, and only the
    // transitions permitted by the state machine (enforced in the service) are accepted.
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DOCTOR','PHARMACIST','ADMIN')")
    public ResponseEntity<PrescriptionDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(prescriptionService.updateStatus(id, status));
    }
}
