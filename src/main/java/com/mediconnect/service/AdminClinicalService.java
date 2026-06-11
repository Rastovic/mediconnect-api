package com.mediconnect.service;

import com.mediconnect.entity.LabResult;
import com.mediconnect.entity.MedicalRecord;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.Prescription;
import com.mediconnect.entity.RefillRequest;
import com.mediconnect.enums.PrescriptionStatus;
import com.mediconnect.enums.RefillStatus;
import com.mediconnect.repository.LabResultRepository;
import com.mediconnect.repository.MedicalRecordRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.PrescriptionRepository;
import com.mediconnect.repository.RefillRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Module E — Clinical Overrides.
//
// Each method is the "admin override" version of an endpoint that normally
// guards a clinical workflow (pharmacist dispenses, doctor authors records,
// lab tech enters results). Every override skips role checks, state machines,
// and the existing eligibility validators — that is the point.
@Service
@RequiredArgsConstructor
public class AdminClinicalService {

    private final PrescriptionRepository prescriptionRepository;
    private final RefillRequestRepository refillRequestRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final LabResultRepository labResultRepository;
    private final PatientRepository patientRepository;

    // -------- Prescriptions --------

    public List<Map<String, Object>> listPrescriptions() {
        return prescriptionRepository.findAll().stream()
                .map(this::toPrescriptionMap)
                .collect(Collectors.toList());
    }

    // [A01] Bypasses PrescriptionService.dispense (which at least requires a
    //        pharmacistId in the body, #109). No pharmacist attribution at all —
    //        the resulting row has pharmacist_id = NULL and a dispensedAt that
    //        wasn't checked against any state machine (already DISPENSED, CANCELLED,
    //        anything is overwritten).
    // [A04] No EligibilityValidator call — patient-allergy checks skipped.
    public Map<String, Object> forceDispense(Long id) {
        Prescription rx = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));
        rx.setStatus(PrescriptionStatus.DISPENSED);
        rx.setDispensedAt(LocalDateTime.now());
        // [A08] pharmacist_id left as-is, even if it was set to a different user.
        //        If null, the audit trail has no actor.
        return toPrescriptionMap(prescriptionRepository.save(rx));
    }

    // [A07] Mass Assignment — any field in the body is overwritten, including
    //        `patientId`. Re-targets the prescription to a different patient
    //        (e.g. take dispensed Atorvastatin away from patient2, attribute it
    //        to patient3 instead — useful for falsifying medication history).
    public Map<String, Object> updatePrescription(Long id, Map<String, Object> body) {
        Prescription rx = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));

        if (body.containsKey("medicationName")) rx.setMedicationName((String) body.get("medicationName"));
        if (body.containsKey("dosage"))         rx.setDosage((String) body.get("dosage"));
        if (body.containsKey("instructions"))   rx.setInstructions((String) body.get("instructions"));
        if (body.containsKey("status"))         rx.setStatus(PrescriptionStatus.valueOf((String) body.get("status")));
        if (body.containsKey("patientId")) {
            // [A07] Re-target the prescription. No check that the new patient even exists.
            Long newPatientId = ((Number) body.get("patientId")).longValue();
            Patient patient = patientRepository.findById(newPatientId)
                    .orElseThrow(() -> new RuntimeException("Patient not found: " + newPatientId));
            rx.setPatient(patient);
        }
        // [A07] dispensedAt mutable — falsify when the dispense supposedly happened
        if (body.containsKey("dispensedAt")) {
            Object v = body.get("dispensedAt");
            rx.setDispensedAt(v == null ? null : LocalDateTime.parse(v.toString()));
        }
        return toPrescriptionMap(prescriptionRepository.save(rx));
    }

    // -------- Refills --------

    public List<Map<String, Object>> listRefills() {
        return refillRequestRepository.findAll().stream()
                .map(this::toRefillMap)
                .collect(Collectors.toList());
    }

    // [A01] Force a refill into APPROVED/READY state, skipping the queue and
    //        the eligibility validator. Compounds the existing A10 refill chain:
    //        a refill flagged FAILED by the validator can be promoted directly
    //        to READY here, then dispensed via the existing /refills/{id}/dispense
    //        endpoint (which has no role check, #128).
    public Map<String, Object> overrideRefill(Long id, Map<String, Object> body) {
        RefillRequest refill = refillRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Refill not found: " + id));
        String targetStatus = body.containsKey("status") ? (String) body.get("status") : "READY";
        refill.setStatus(RefillStatus.valueOf(targetStatus));
        refill.setFailureReason(null);  // clear any prior failure
        if (body.containsKey("quantity")) {
            // [A07] Caller may pass null to feed the existing A10 fail-open NPE chain.
            Object q = body.get("quantity");
            refill.setQuantity(q == null ? null : ((Number) q).intValue());
        }
        return toRefillMap(refillRequestRepository.save(refill));
    }

    // -------- Medical Records --------

    public List<Map<String, Object>> listMedicalRecords() {
        return medicalRecordRepository.findAll().stream()
                .map(this::toMedicalRecordMap)
                .collect(Collectors.toList());
    }

    // [A09] Hard delete of a clinical record. No retention, no archival.
    //        HIPAA/GDPR require medical records be retained for years —
    //        this endpoint removes them in one call with no audit besides
    //        the interceptor's HTTP log (which #160 can selectively delete).
    public Map<String, Object> deleteMedicalRecord(Long id) {
        medicalRecordRepository.deleteById(id);
        return Map.of("deleted", true, "id", id);
    }

    // -------- Lab Results --------

    public List<Map<String, Object>> listLabResults() {
        return labResultRepository.findAll().stream()
                .map(this::toLabResultMap)
                .collect(Collectors.toList());
    }

    // [A08] Software & Data Integrity Failure — mutates the numeric `resultValue`
    //        and `referenceRange` without setting any "amended" flag, without
    //        preserving the original value, and without changing `testDate`.
    //        A clinician opening the record sees the falsified result as if
    //        it had always been that way. The audit log records the HTTP call
    //        but the response captures only the new value (the old value is
    //        gone — see ContentCachingFilter only captures the response body).
    public Map<String, Object> overrideLabResultValue(Long id, Map<String, Object> body) {
        LabResult lab = labResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab result not found: " + id));
        if (body.containsKey("resultValue"))    lab.setResultValue(String.valueOf(body.get("resultValue")));
        if (body.containsKey("unit"))           lab.setUnit((String) body.get("unit"));
        if (body.containsKey("referenceRange")) lab.setReferenceRange((String) body.get("referenceRange"));
        if (body.containsKey("notes"))          lab.setNotes((String) body.get("notes"));
        // [A08] Deliberately NOT changing labTech (the original tech still owns
        //        the amended record) and NOT touching testDate. The result reads
        //        as authoritative.
        return toLabResultMap(labResultRepository.save(lab));
    }

    // -------- Mappers --------

    private Map<String, Object> toPrescriptionMap(Prescription rx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rx.getId());
        m.put("medicationName", rx.getMedicationName());
        m.put("dosage", rx.getDosage());
        m.put("instructions", rx.getInstructions());
        m.put("status", rx.getStatus() != null ? rx.getStatus().name() : null);
        m.put("patientId", rx.getPatient() != null ? rx.getPatient().getId() : null);
        m.put("doctorId", rx.getDoctor() != null ? rx.getDoctor().getId() : null);
        m.put("pharmacistId", rx.getPharmacist() != null ? rx.getPharmacist().getId() : null);
        m.put("createdAt", rx.getCreatedAt());
        m.put("dispensedAt", rx.getDispensedAt());
        return m;
    }

    private Map<String, Object> toRefillMap(RefillRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("prescriptionId", r.getPrescriptionId());
        m.put("patientId", r.getPatientId());
        m.put("quantity", r.getQuantity());
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("failureReason", r.getFailureReason());
        m.put("retryCount", r.getRetryCount());
        m.put("pharmacistId", r.getPharmacistId());
        m.put("dispensedAt", r.getDispensedAt());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private Map<String, Object> toMedicalRecordMap(MedicalRecord rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rec.getId());
        m.put("patientId", rec.getPatient() != null ? rec.getPatient().getId() : null);
        m.put("doctorId", rec.getDoctor() != null ? rec.getDoctor().getId() : null);
        m.put("diagnosis", rec.getDiagnosis());
        m.put("prescription", rec.getPrescription());
        m.put("createdAt", rec.getCreatedAt());
        return m;
    }

    private Map<String, Object> toLabResultMap(LabResult lab) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", lab.getId());
        m.put("patientId", lab.getPatient() != null ? lab.getPatient().getId() : null);
        m.put("labTechId", lab.getLabTech() != null ? lab.getLabTech().getId() : null);
        m.put("testName", lab.getTestName());
        m.put("resultValue", lab.getResultValue());
        m.put("unit", lab.getUnit());
        m.put("referenceRange", lab.getReferenceRange());
        m.put("status", lab.getStatus() != null ? lab.getStatus().name() : null);
        m.put("testDate", lab.getTestDate());
        m.put("notes", lab.getNotes());
        return m;
    }
}
