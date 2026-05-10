package com.mediconnect.controller;

import com.mediconnect.dto.PrescriptionDto;
import com.mediconnect.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// [A06] PUT /{id}/dispense — no check that the prescription is not already DISPENSED.
// [A06] PUT /{id}/status  — any status transition accepted, including CANCELLED → DISPENSED.
// [A01] No role enforcement — a PATIENT can call dispense or change status.
@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public ResponseEntity<PrescriptionDto> create(@RequestBody PrescriptionDto dto) {
        return ResponseEntity.status(201).body(prescriptionService.create(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrescriptionDto> getById(@PathVariable Long id) {
        // [A01] No check that the caller is the patient or prescribing doctor
        return ResponseEntity.ok(prescriptionService.findById(id));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<PrescriptionDto>> getByPatient(@PathVariable Long patientId) {
        // [A01] No check that caller is the patient or their doctor
        return ResponseEntity.ok(prescriptionService.findByPatientId(patientId));
    }

    // [A06] Security Misconfiguration / Missing State Machine:
    //        No guard against dispensing a prescription that is already DISPENSED or CANCELLED.
    //
    //        Allowed business transition:  CREATED → DISPENSED (once)
    //        What this allows:
    //          DISPENSED → DISPENSED  (double dispensing — duplicate medication supply / billing fraud)
    //          CANCELLED → DISPENSED  (dispensing a voided prescription — pharmaceutical fraud)
    //
    // [A07] pharmacistId supplied in request body — a caller can attribute the dispensing
    //        action to any pharmacist user without authentication or authorization.
    //
    // [A01] No role check — any caller (PATIENT, DOCTOR, LAB_TECH) can dispense.
    @PutMapping("/{id}/dispense")
    public ResponseEntity<PrescriptionDto> dispense(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long pharmacistId = body.get("pharmacistId");
        return ResponseEntity.ok(prescriptionService.dispense(id, pharmacistId));
    }

    // [A06] No state machine validation — any status string is accepted and written
    //        directly to the prescription without checking the current state.
    //
    //        Illegal transitions this allows:
    //          DISPENSED → CREATED    (re-opens a dispensed prescription for re-use)
    //          DISPENSED → CANCELLED  (erases audit trail after medication is already issued)
    //          CANCELLED → DISPENSED  (dispenses a previously cancelled prescription)
    //          CANCELLED → CREATED    (reactivates a void prescription)
    //
    // [A01] No role enforcement — a PATIENT can promote their own prescription to DISPENSED.
    @PutMapping("/{id}/status")
    public ResponseEntity<PrescriptionDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(prescriptionService.updateStatus(id, status));
    }
}
