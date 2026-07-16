package com.mediconnect.service;

import com.mediconnect.dto.MedicalRecordDto;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.MedicalRecord;
import com.mediconnect.entity.Patient;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.MedicalRecordRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final CurrentUserService currentUserService;

    // [A04] Upload directory visible in config — exposed via /actuator/env
    @Value("${app.upload-dir:/tmp/mediconnect/uploads/}")
    private String uploadDir;

    // [A01] Broken Access Control — no verification that the doctor is assigned
    //        to this patient or that they share an appointment.
    //        Any doctor can create a medical record for any patient without
    //        ever having treated them. No appointment ownership check.
    public MedicalRecordDto create(MedicalRecordDto dto) {
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found: " + dto.getPatientId()));
        Doctor doctor = doctorRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + dto.getDoctorId()));

        // [A01] No check: does an Appointment exist linking this doctor and patient?
        //        No check: is this doctor authorized for this patient's care plan?
        Appointment appointment = null;
        if (dto.getAppointmentId() != null) {
            appointment = appointmentRepository.findById(dto.getAppointmentId()).orElse(null);
            // [A01] appointment.doctor_id vs dto.getDoctorId() is never compared
        }

        // notes from frontend maps to prescription column
        String prescriptionValue = (dto.getNotes() != null && !dto.getNotes().isBlank())
                ? dto.getNotes() : dto.getPrescription();

        MedicalRecord record = MedicalRecord.builder()
                .patient(patient)
                .doctor(doctor)
                .appointment(appointment)
                .diagnosis(dto.getDiagnosis())
                .prescription(prescriptionValue)
                .createdAt(LocalDateTime.now())
                // [A08] attachmentPath stored without content_hash
                .build();

        return toDto(medicalRecordRepository.save(record));
    }

    // [A05] Unrestricted File Upload + Path Traversal write vector.
    //
    //  Attack 1 — Unrestricted extension:
    //    Upload "shell.php", "evil.jsp", "malware.exe" → no MIME or extension check.
    //
    //  Attack 2 — Path Traversal via getOriginalFilename():
    //    filename = "../../etc/cron.d/backdoor"
    //    storagePath = "/tmp/mediconnect/uploads/../../etc/cron.d/backdoor"
    //    After resolution → writes to /etc/cron.d/backdoor
    //
    //  Attack 3 — Null byte injection (older JVMs):
    //    filename = "evil.php\0.pdf" → stored as "evil.php" on some OS/JVM combos.
    //
    // [A08] No content_hash computed or stored — uploaded file integrity is unverifiable.
    public String uploadAttachment(Long recordId, MultipartFile file) throws IOException {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Medical record not found: " + recordId));

        // Server-generated filename — the client name is never used for the path.
        String ext = safeExtension(file.getOriginalFilename());
        String safeName = java.util.UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path destination = base.resolve(safeName).normalize();
        if (!destination.startsWith(base)) {
            throw new SecurityException("Path traversal");
        }

        Files.createDirectories(base);
        Files.copy(file.getInputStream(), destination);

        record.setAttachmentPath(destination.toString());
        record.setContentHash(sha256(destination));
        medicalRecordRepository.save(record);

        return destination.toString();
    }

    private String sha256(Path p) throws IOException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static final java.util.Set<String> ALLOWED_EXT =
            java.util.Set.of("pdf", "png", "jpg", "jpeg", "dcm");

    private String safeExtension(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) return "";
        String ext = originalName.substring(dot + 1).toLowerCase();
        if (!ext.matches("[a-z0-9]{1,5}") || !ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type");
        }
        return ext;
    }

    // [A05] Path Traversal read vector — filePath query parameter used directly.
    //
    //  Attack examples:
    //    ?filePath=/etc/passwd
    //    ?filePath=/etc/shadow
    //    ?filePath=../../../home/ubuntu/.ssh/id_rsa
    //    ?filePath=/tmp/mediconnect/uploads/../../../../etc/mysql/my.cnf
    //
    //  No canonical path check, no startsWith(uploadDir) boundary enforcement,
    //  no check that the record's own attachmentPath matches filePath.
    /** Reads the attachment for a record from its stored path only — no client-supplied path. */
    public byte[] downloadAttachment(Long recordId) throws IOException {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Medical record not found: " + recordId));
        String stored = record.getAttachmentPath();
        if (stored == null || stored.isBlank()) {
            throw new RuntimeException("No attachment");
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path path = Paths.get(stored).toAbsolutePath().normalize();
        if (!path.startsWith(base) || !Files.exists(path)) {
            throw new SecurityException("Path traversal");
        }
        return Files.readAllBytes(path);
    }

    public MedicalRecordDto findById(Long id) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical record not found: " + id));
        return toDto(record);
    }

    // Principal-scoped: a PATIENT only ever sees their own records; staff
    // (doctor/pharmacist/lab-tech/admin) see all. Caller identity comes from the
    // SecurityContext, so a patient cannot widen the result set.
    public List<MedicalRecordDto> findAll() {
        if (currentUserService.isPatient()) {
            Long patientId = currentUserService.currentPatientId().orElse(-1L);
            return medicalRecordRepository.findByPatientId(patientId)
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
        return medicalRecordRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<MedicalRecordDto> findByPatientId(Long patientId) {
        // [A01] No check that the caller is the patient or their treating doctor
        return medicalRecordRepository.findByPatientId(patientId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // [A01] No ownership check — any authenticated user can update any medical record.
    //        Diagnosis and notes (stored in prescription column) are overwritten without
    //        verifying that the caller is the treating doctor or the patient's guardian.
    public MedicalRecordDto update(Long id, MedicalRecordDto dto) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical record not found: " + id));
        if (dto.getDiagnosis() != null) record.setDiagnosis(dto.getDiagnosis());
        // notes from frontend maps to the prescription column; null = don't touch, "" = clear
        if (dto.getNotes() != null) record.setPrescription(dto.getNotes());
        return toDto(medicalRecordRepository.save(record));
    }

    private static String fullName(com.mediconnect.entity.User u) {
        String f = u.getFirstName(), l = u.getLastName();
        return (f != null && !f.isBlank() && l != null && !l.isBlank()) ? f + " " + l : u.getUsername();
    }

    private MedicalRecordDto toDto(MedicalRecord r) {
        return MedicalRecordDto.builder()
                .id(r.getId())
                .patientId(r.getPatient().getId())
                .doctorId(r.getDoctor().getId())
                .patientName(fullName(r.getPatient().getUser()))
                .doctorName(fullName(r.getDoctor().getUser()))
                .appointmentId(r.getAppointment() != null ? r.getAppointment().getId() : null)
                .diagnosis(r.getDiagnosis())
                .prescription(r.getPrescription())
                .notes(r.getPrescription())
                // [A08] attachmentPath exposed — no hash, no signed URL
                .attachmentPath(r.getAttachmentPath())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
