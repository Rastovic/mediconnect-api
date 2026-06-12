package com.mediconnect.service;

import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.PasswordUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Module B — Clinical Staff Onboarding
//
// New surface: license verification (trust-the-client claim), per-doctor edit,
// and a one-shot staff onboarding form that creates a User + role-specific
// profile and stores an uploaded license document — with the same path-traversal
// bug as MedicalRecordService.uploadAttachment.
@Service
@RequiredArgsConstructor
public class AdminStaffService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordUtils passwordUtils;

    @Value("${app.upload-dir:/tmp/mediconnect/uploads/}")
    private String uploadDir;

    // [A04] Insecure Design — "verification" is a single boolean flip with
    //        no check against any external licensing registry. The caller
    //        simply asserts that the license number is real; the system
    //        believes them.
    public Map<String, Object> verifyLicense(Long doctorId, Map<String, Object> body) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + doctorId));

        if (body.containsKey("licenseNumber")) {
            // [A07] License number mutable at the same time we "verify" it —
            //        attacker can set any number and immediately mark it valid.
            doctor.setLicenseNumber((String) body.get("licenseNumber"));
        }
        // [A04] Whatever the caller passes for `verified` is accepted as truth.
        boolean verified = body.containsKey("verified")
                ? Boolean.TRUE.equals(body.get("verified"))
                : true;
        doctor.setLicenseVerified(verified);
        doctorRepository.save(doctor);

        return Map.of(
                "doctorId", doctorId,
                "licenseNumber", doctor.getLicenseNumber(),
                "verified", doctor.getLicenseVerified(),
                "verifiedAt", LocalDateTime.now().toString()
        );
    }

    // [A07] Mass Assignment — every doctor field except `user` is patchable
    //        from the body. Combined with #174 (admin can re-target a
    //        prescription's patient) this lets an attacker forge a complete
    //        falsified "doctor → prescription → patient" chain.
    public Map<String, Object> updateDoctor(Long id, Map<String, Object> body) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + id));

        if (body.containsKey("specialty"))     doctor.setSpecialty((String) body.get("specialty"));
        if (body.containsKey("licenseNumber")) doctor.setLicenseNumber((String) body.get("licenseNumber"));
        if (body.containsKey("hospital"))      doctor.setHospital((String) body.get("hospital"));
        if (body.containsKey("phone"))         doctor.setPhone((String) body.get("phone"));
        if (body.containsKey("bio"))           doctor.setBio((String) body.get("bio"));
        if (body.containsKey("licenseVerified")) {
            // [A04] Caller can re-verify (or un-verify) without any check.
            doctor.setLicenseVerified(Boolean.TRUE.equals(body.get("licenseVerified")));
        }
        return toDoctorMap(doctorRepository.save(doctor));
    }

    public List<Map<String, Object>> listDoctors() {
        return doctorRepository.findAll().stream()
                .map(this::toDoctorMap)
                .collect(Collectors.toList());
    }

    // [A05] Path Traversal write via getOriginalFilename() — same pattern as
    //        MedicalRecordService.uploadAttachment (the existing demo). Combined
    //        here with [A07] mass-assignment of the role + license number on
    //        the same multipart submission, so a single endpoint creates a
    //        new ADMIN user **and** writes an attacker-controlled file path.
    //
    //  Attack — write a webshell to a controlled location:
    //    POST /api/admin/staff/onboard
    //      multipart fields:
    //        role=DOCTOR
    //        username=evil&email=e@x.com&password=p
    //        licenseNumber=LIC-FAKE-9999
    //        specialty=Cardiology
    //        licenseDocument: filename="../../../../tmp/shell.jsp" content=...
    //
    // [A06] password stored as MD5; [A07] role accepted verbatim.
    public Map<String, Object> onboardStaff(Map<String, String> fields,
                                            MultipartFile licenseDocument) throws IOException {
        String roleStr = fields.getOrDefault("role", "PATIENT");
        Role role = Role.valueOf(roleStr);

        // [A07] role accepted from form data — including ADMIN
        User user = User.builder()
                .username(fields.get("username"))
                .email(fields.get("email"))
                // [A06] MD5(password) with no salt
                .passwordHash(passwordUtils.hashPassword(fields.getOrDefault("password", "changeme")))
                .role(role)
                .active(true)
                .createdAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .firstName(fields.get("firstName"))
                .lastName(fields.get("lastName"))
                .phone(fields.get("phone"))
                .build();
        user = userRepository.save(user);

        String storedPath = null;
        if (licenseDocument != null && !licenseDocument.isEmpty()) {
            // [A05] Path Traversal write — getOriginalFilename() is fully
            //        attacker-controlled; concatenated into uploadDir with no
            //        normalisation, no startsWith check, no extension whitelist.
            String filename = licenseDocument.getOriginalFilename();
            String storagePath = uploadDir + filename;
            Path destination = Paths.get(storagePath);
            Files.createDirectories(destination.getParent());
            // [A05] REPLACE_EXISTING — attacker can overwrite arbitrary files
            //        if the path traversal lands on a writable target.
            Files.copy(licenseDocument.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            storedPath = storagePath;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole().name());
        result.put("licenseDocumentPath", storedPath);
        // [A05] Filename returned verbatim — UI renders it via
        //        dangerouslySetInnerHTML in AdminOnboardingPage, so a filename
        //        like '<img src=x onerror=...>' becomes stored XSS for any
        //        subsequent admin who opens the staff list.
        result.put("uploadedFilename", licenseDocument != null ? licenseDocument.getOriginalFilename() : null);

        // Role-specific profile rows
        if (role == Role.DOCTOR) {
            Doctor doctor = Doctor.builder()
                    .user(user)
                    .specialty(fields.get("specialty"))
                    .licenseNumber(fields.get("licenseNumber"))
                    .hospital(fields.get("hospital"))
                    .phone(fields.get("phone"))
                    .bio(fields.get("bio"))
                    // [A04] Client may set verified=true at onboarding time
                    //        in the same multipart form (no second verification step).
                    .licenseVerified(Boolean.parseBoolean(fields.getOrDefault("verified", "false")))
                    .licenseDocumentPath(storedPath)
                    .build();
            doctor = doctorRepository.save(doctor);
            result.put("doctorId", doctor.getId());
        } else if (role == Role.PATIENT) {
            Patient patient = Patient.builder()
                    .user(user)
                    .dateOfBirth(parseDateOrNull(fields.get("dateOfBirth")))
                    .bloodType(fields.get("bloodType"))
                    .allergies(fields.get("allergies"))
                    .insuranceNumber(fields.get("insuranceNumber"))
                    .emergencyContact(fields.get("emergencyContact"))
                    .build();
            patient = patientRepository.save(patient);
            result.put("patientId", patient.getId());
        }
        // LAB_TECH / PHARMACIST / ADMIN have no role-specific table — just the User row.

        return result;
    }

    private Map<String, Object> toDoctorMap(Doctor d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("userId", d.getUser() != null ? d.getUser().getId() : null);
        m.put("username", d.getUser() != null ? d.getUser().getUsername() : null);
        m.put("email", d.getUser() != null ? d.getUser().getEmail() : null);
        m.put("specialty", d.getSpecialty());
        m.put("licenseNumber", d.getLicenseNumber());
        m.put("hospital", d.getHospital());
        m.put("phone", d.getPhone());
        m.put("bio", d.getBio());
        m.put("licenseVerified", Boolean.TRUE.equals(d.getLicenseVerified()));
        // [A05] Path returned verbatim so the UI can link to it.
        m.put("licenseDocumentPath", d.getLicenseDocumentPath());
        return m;
    }

    private java.time.LocalDate parseDateOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return java.time.LocalDate.parse(s); } catch (Exception e) { return null; }
    }
}
