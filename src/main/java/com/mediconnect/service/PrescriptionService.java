package com.mediconnect.service;

import com.mediconnect.dto.PrescriptionDto;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.MedicalRecord;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.Prescription;
import com.mediconnect.entity.User;
import com.mediconnect.enums.PrescriptionStatus;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.MedicalRecordRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.PrescriptionRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    // [A02] Missing state machine — a prescription can be dispensed regardless
    //        of its current status. No guard against double dispensing or dispensing
    //        a cancelled prescription.
    //
    //  Allowed business transition: CREATED → DISPENSED (once)
    //  What this allows:
    //    DISPENSED → DISPENSED  (double dispensing — duplicate drug supply / billing fraud)
    //    CANCELLED → DISPENSED  (dispensing a voided prescription — pharmaceutical fraud)
    //
    // [A07] pharmacistId taken from request body — caller can attribute the dispensing
    //        action to any pharmacist user without any authentication or role verification.
    //
    // [A01] No role enforcement — a PATIENT or DOCTOR can call this endpoint and dispense.
    //
    //  Secure implementation would require:
    //    if (prescription.getStatus() != PrescriptionStatus.CREATED) {
    //        throw new IllegalStateException("Only CREATED prescriptions can be dispensed");
    //    }
    // State machine enforced: only a CREATED prescription may be dispensed, exactly once.
    // The dispensing pharmacist is taken from the authenticated principal, never from the
    // request body, so the action cannot be attributed to an arbitrary user. Role is
    // gated at the controller (PHARMACIST/ADMIN).
    public PrescriptionDto dispense(Long id, Long pharmacistId) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));

        if (prescription.getStatus() != PrescriptionStatus.CREATED) {
            throw new IllegalStateException("Only CREATED prescriptions can be dispensed");
        }

        User pharmacist = currentUserService.requireUser();

        prescription.setStatus(PrescriptionStatus.DISPENSED);
        prescription.setPharmacist(pharmacist);
        prescription.setDispensedAt(LocalDateTime.now());

        return toDto(prescriptionRepository.save(prescription));
    }

    // Only the transitions allowed by the business state machine are accepted.
    private static void assertAllowedTransition(PrescriptionStatus from, PrescriptionStatus to) {
        boolean allowed = switch (from) {
            case CREATED -> to == PrescriptionStatus.DISPENSED || to == PrescriptionStatus.CANCELLED;
            case DISPENSED, CANCELLED -> false; // terminal states
        };
        if (!allowed) {
            throw new IllegalStateException("Illegal prescription transition: " + from + " -> " + to);
        }
    }

    // [A02] No state machine validation — any status transition is accepted and
    //        written directly without checking the current state.
    //
    //  Illegal transitions this enables:
    //    DISPENSED → CREATED    (re-opens a dispensed prescription for re-use)
    //    DISPENSED → CANCELLED  (erases audit trail after medication has already been issued)
    //    CANCELLED → DISPENSED  (dispenses a previously voided prescription)
    //    CANCELLED → CREATED    (reactivates a void prescription)
    //
    // [A01] No role enforcement — a PATIENT can promote their own prescription to DISPENSED.
    public PrescriptionDto updateStatus(Long id, String status) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));

        PrescriptionStatus target;
        try {
            target = PrescriptionStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown prescription status: " + status);
        }

        assertAllowedTransition(prescription.getStatus(), target);
        prescription.setStatus(target);

        if (target == PrescriptionStatus.DISPENSED && prescription.getDispensedAt() == null) {
            prescription.setDispensedAt(LocalDateTime.now());
            prescription.setPharmacist(currentUserService.requireUser());
        }

        return toDto(prescriptionRepository.save(prescription));
    }

    public PrescriptionDto create(PrescriptionDto dto) {
        MedicalRecord record = medicalRecordRepository.findById(dto.getMedicalRecordId())
                .orElseThrow(() -> new RuntimeException("Medical record not found: " + dto.getMedicalRecordId()));
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found: " + dto.getPatientId()));
        Doctor doctor = doctorRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + dto.getDoctorId()));

        Prescription prescription = Prescription.builder()
                .medicalRecord(record)
                .patient(patient)
                .doctor(doctor)
                .medicationName(dto.getMedicationName())
                .dosage(dto.getDosage())
                .instructions(dto.getInstructions())
                .status(PrescriptionStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(prescriptionRepository.save(prescription));
    }

    public PrescriptionDto findById(Long id) {
        // [A01] No check that the caller is the patient or the prescribing doctor
        return toDto(prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id)));
    }

    public List<PrescriptionDto> findByPatientId(Long patientId) {
        // [A01] No check that the caller is the patient
        return prescriptionRepository.findByPatientId(patientId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // Principal-scoped: a PATIENT only ever sees their own prescriptions; staff
    // (doctor/pharmacist/lab-tech/admin) see the full list. The caller identity comes
    // from the SecurityContext, so a patient cannot widen the result set.
    public List<PrescriptionDto> findAll() {
        if (currentUserService.isPatient()) {
            Long patientId = currentUserService.currentPatientId().orElse(-1L);
            return prescriptionRepository.findByPatientId(patientId)
                    .stream().map(this::toDto).collect(Collectors.toList());
        }
        return prescriptionRepository.findAll()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    private static String fullName(User u) {
        String f = u.getFirstName(), l = u.getLastName();
        return (f != null && !f.isBlank() && l != null && !l.isBlank()) ? f + " " + l : u.getUsername();
    }

    private PrescriptionDto toDto(Prescription p) {
        return PrescriptionDto.builder()
                .id(p.getId())
                .medicalRecordId(p.getMedicalRecord() == null ? null : p.getMedicalRecord().getId())
                .patientId(p.getPatient().getId())
                .patientName(fullName(p.getPatient().getUser()))
                .doctorId(p.getDoctor().getId())
                .doctorName(fullName(p.getDoctor().getUser()))
                .pharmacistId(p.getPharmacist() != null ? p.getPharmacist().getId() : null)
                .medicationName(p.getMedicationName())
                .dosage(p.getDosage())
                .instructions(p.getInstructions())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .dispensedAt(p.getDispensedAt())
                .build();
    }
}
