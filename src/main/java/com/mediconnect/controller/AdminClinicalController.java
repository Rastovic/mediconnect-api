package com.mediconnect.controller;

import com.mediconnect.service.AdminClinicalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module E — Clinical Overrides
//
// [A01] All endpoints under permitAll(). No role check.
//
//   GET    /prescriptions                       — list (admin view)
//   POST   /prescriptions/{id}/force-dispense   — bypass pharmacist workflow (A01)
//   PUT    /prescriptions/{id}                  — mass assign incl. patientId (A07)
//   GET    /refills                             — list
//   POST   /refills/{id}/override               — force status / quantity (A01)
//   GET    /medical-records                     — list
//   DELETE /medical-records/{id}                — hard delete (A09)
//   GET    /lab-results                         — list
//   POST   /lab-results/{id}/override-value     — mutate value, no amend flag (A08)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminClinicalController {

    private final AdminClinicalService adminClinicalService;

    @GetMapping("/prescriptions")
    public ResponseEntity<List<Map<String, Object>>> listPrescriptions() {
        return ResponseEntity.ok(adminClinicalService.listPrescriptions());
    }

    @PostMapping("/prescriptions/{id}/force-dispense")
    public ResponseEntity<Map<String, Object>> forceDispense(@PathVariable Long id) {
        return ResponseEntity.ok(adminClinicalService.forceDispense(id));
    }

    @PutMapping("/prescriptions/{id}")
    public ResponseEntity<Map<String, Object>> updatePrescription(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminClinicalService.updatePrescription(id, body));
    }

    @GetMapping("/refills")
    public ResponseEntity<List<Map<String, Object>>> listRefills() {
        return ResponseEntity.ok(adminClinicalService.listRefills());
    }

    @PostMapping("/refills/{id}/override")
    public ResponseEntity<Map<String, Object>> overrideRefill(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(adminClinicalService.overrideRefill(id, body == null ? Map.of() : body));
    }

    @GetMapping("/medical-records")
    public ResponseEntity<List<Map<String, Object>>> listMedicalRecords() {
        return ResponseEntity.ok(adminClinicalService.listMedicalRecords());
    }

    @DeleteMapping("/medical-records/{id}")
    public ResponseEntity<Map<String, Object>> deleteMedicalRecord(@PathVariable Long id) {
        return ResponseEntity.ok(adminClinicalService.deleteMedicalRecord(id));
    }

    @GetMapping("/lab-results")
    public ResponseEntity<List<Map<String, Object>>> listLabResults() {
        return ResponseEntity.ok(adminClinicalService.listLabResults());
    }

    @PostMapping("/lab-results/{id}/override-value")
    public ResponseEntity<Map<String, Object>> overrideLabValue(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminClinicalService.overrideLabResultValue(id, body));
    }
}
