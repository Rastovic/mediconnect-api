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

    // [A02] Upload directory visible in config — exposed via /actuator/env
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

    // [A03] Unrestricted File Upload + Path Traversal write vector.
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

        // [A03] getOriginalFilename() — fully attacker-controlled value, no sanitization
        String filename = file.getOriginalFilename();

        // [A03] Direct string concatenation — no Paths.get(uploadDir).resolve() with
        //        toAbsolutePath().normalize() and startsWith(uploadDir) check
        String storagePath = uploadDir + filename;
        Path destination = Paths.get(storagePath);

        Files.createDirectories(destination.getParent());
        // [A03] REPLACE_EXISTING — attacker can overwrite arbitrary files if path traversal succeeds
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        // [A08] content_hash intentionally not computed or persisted
        // Secure: String hash = DigestUtils.md5DigestAsHex(file.getBytes());
        //         record.setContentHash(hash);

        record.setAttachmentPath(storagePath);
        medicalRecordRepository.save(record);

        return storagePath;
    }

    // [A03] Path Traversal read vector — filePath query parameter used directly.
    //
    //  Attack examples:
    //    ?filePath=/etc/passwd
    //    ?filePath=/etc/shadow
    //    ?filePath=../../../home/ubuntu/.ssh/id_rsa
    //    ?filePath=/tmp/mediconnect/uploads/../../../../etc/mysql/my.cnf
    //
    //  No canonical path check, no startsWith(uploadDir) boundary enforcement,
    //  no check that the record's own attachmentPath matches filePath.
    public byte[] downloadAttachment(String filePath) throws IOException {
        // [A03] filePath taken verbatim from query parameter — attacker controls the path
        Path path = Paths.get(filePath);

        // Secure implementation would require:
        //   Path canonical = path.toAbsolutePath().normalize();
        //   Path base      = Paths.get(uploadDir).toAbsolutePath().normalize();
        //   if (!canonical.startsWith(base)) throw new SecurityException("Path traversal");

        if (!Files.exists(path)) {
            throw new RuntimeException("File not found: " + filePath);
        }

        // [A03] Reads any file accessible to the JVM process — no boundary check
        return Files.readAllBytes(path);
    }

    public MedicalRecordDto findById(Long id) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medical record not found: " + id));
        return toDto(record);
    }

    // [A01] No access control — any caller gets all records
    public List<MedicalRecordDto> findAll() {
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
