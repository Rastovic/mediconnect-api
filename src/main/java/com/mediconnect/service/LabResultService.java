package com.mediconnect.service;

import com.mediconnect.dto.LabResultDto;
import com.mediconnect.entity.LabResult;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.User;
import com.mediconnect.enums.LabResultStatus;
import com.mediconnect.repository.LabResultRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class LabResultService {

    private final LabResultRepository labResultRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload-dir:/tmp/mediconnect/uploads/}")
    private String uploadDir;

    // [A05] SQL Injection — četiri parametra konkatenisana direktno u SQL string.
    //
    //  Parametri sa navodnicima (string kontekst) — klasična string injection:
    //    testName  → LIKE '%<input>%'
    //    testCode  → LIKE '%<input>%' (pretražuje reference_range)
    //    status    → LIKE '%<input>%'
    //
    //  patientId — NUMERIČKI, bez navodnika (UNION napad bez zatvaranja stringa):
    //    Normalno : patient_id = 5
    //    Napad    : patient_id = 0 UNION SELECT id,patient_id,lab_tech_id,test_name,
    //                               password_hash,email,NULL,role,NOW(),notes,NULL FROM ... --
    //    → Napadač ne mora da zatvori string navodnike jer ih nikada nije ni otvorio.
    //       Ovo je najopasniji oblik jer WHERE uslov izgleda "siguran" razvojnom programeru.
    //
    //  String napadi:
    //    testName = "' OR '1'='1"     → vraća sve rezultate
    //    testName = "' OR 1=1 --"     → zaobilazi sve filtere
    //    status   = "' UNION SELECT username,password_hash,3,4,5,6,7,8,9,10,11 FROM users --"
    //                                 → dump users tablice kroz status filter
    public List<LabResultDto> searchLabResults(String testName,
                                               String testCode,
                                               String status,
                                               Long patientId) {
        String sql =
                "SELECT lr.id, lr.patient_id, lr.lab_tech_id, lr.test_name, " +
                "       lr.result_value, lr.unit, lr.reference_range, lr.status, " +
                "       lr.test_date, lr.notes, lr.attachment_path, " +
                "       up.username AS patient_name " +
                "FROM lab_results lr " +
                "JOIN patients p ON lr.patient_id = p.id " +
                "JOIN users up ON p.user_id = up.id " +
                "WHERE lr.test_name       LIKE '%" + (testName != null ? testName : "") + "%' " + // [A05] string injection
                "AND   lr.reference_range LIKE '%" + (testCode != null ? testCode : "") + "%' " + // [A05] string injection
                "AND   lr.status          LIKE '%" + (status   != null ? status   : "") + "%' " + // [A05] string injection
                // [A01] null patientId → no filter → all patients' results exposed to any caller
                (patientId != null ? "AND lr.patient_id = " + patientId : ""); // [A05] no quotes — UNION-ready

        return jdbcTemplate.query(sql, (rs, rowNum) -> LabResultDto.builder()
                .id(rs.getLong("id"))
                .patientId(rs.getLong("patient_id"))
                .patientName(rs.getString("patient_name"))
                .labTechId(rs.getLong("lab_tech_id"))
                .testName(rs.getString("test_name"))
                .resultValue(rs.getString("result_value"))
                .unit(rs.getString("unit"))
                .referenceRange(rs.getString("reference_range"))
                .status(LabResultStatus.valueOf(rs.getString("status")))
                .resultDate(rs.getObject("test_date", LocalDateTime.class))
                .notes(rs.getString("notes"))
                .attachmentPath(rs.getString("attachment_path"))
                .build());
    }

    // [A01] IDOR — nema provere da li je autentifikovani korisnik vlasnik ovog nalaza
    //        ili doktor koji je naručio analizu. Bilo koji korisnik može dohvatiti
    //        laboratorijski rezultat bilo kog pacijenta iteracijom ID vrednosti.
    public LabResultDto findById(Long id) {
        LabResult lr = labResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab result not found: " + id));
        return toDto(lr);
    }

    // [A03] Unrestricted File Upload — nema validacije ekstenzije, MIME tipa ni veličine.
    // [A03] Predvidivo ime fajla — getOriginalFilename() čuva se direktno bez UUID randomizacije.
    //
    //  Problem 1 — Predvidivo ime:
    //    Napadač zna putanju fajla čim poznaje patientId:
    //    /tmp/mediconnect/uploads/bloodwork.pdf  (uvek isti naziv)
    //    Legitiman fajl može biti pregažen upload-om sa istim imenom (TOCTOU).
    //
    //  Problem 2 — Unrestricted extension:
    //    Upload: evil.jsp, shell.php, malware.exe — nema provere tipa sadržaja.
    //
    //  Problem 3 — Path Traversal write:
    //    filename = "../../etc/cron.d/evil"
    //    → piše van uploadDir direktorijuma.
    public String saveAttachment(Long id, MultipartFile file) throws IOException {
        LabResult lr = labResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab result not found: " + id));

        // [A03] getOriginalFilename() — potpuno pod kontrolom napadača, bez sanitizacije
        String filename = file.getOriginalFilename();

        // [A03] Direktna konkatenacija — nema normalize(), nema UUID prefiksa
        // Sigurno: String filename = UUID.randomUUID() + "_" + originalName;
        String storagePath = uploadDir + filename;
        Path destination = Paths.get(storagePath);

        Files.createDirectories(destination.getParent());
        // [A03] REPLACE_EXISTING — legitimni fajl može biti preguzan
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        lr.setAttachmentPath(storagePath);
        labResultRepository.save(lr);

        // [A02] Puna putanja vraćena klijentu — otkriva strukturu fajl sistema
        return storagePath;
    }

    // [A03] Path Traversal (read) — filePath parametar korišćen verbatim.
    //
    //  Primeri napada:
    //    filePath = /etc/passwd
    //    filePath = /etc/shadow
    //    filePath = /proc/self/environ          → environment variables (DB lozinka)
    //    filePath = ../../../root/.ssh/id_rsa   → SSH privatni ključ
    //    filePath = /tmp/mediconnect/uploads/../../application.yaml
    //
    //  Nema: path.toAbsolutePath().normalize().startsWith(base) provere.
    public byte[] downloadFile(String filePath) throws IOException {
        // [A03] Paths.get() prima string direktno — nema boundary check
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new RuntimeException("File not found: " + filePath);
        }
        // [A03] Čita bilo koji fajl dostupan JVM procesu bez ograničenja
        return Files.readAllBytes(path);
    }

    public LabResultDto create(LabResultDto dto) {
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found: " + dto.getPatientId()));
        // Resolve lab tech from JWT — frontend does not send labTechId
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User labTech = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + username));

        LabResult lr = LabResult.builder()
                .patient(patient)
                .labTech(labTech)
                .testName(dto.getTestName())
                .resultValue(dto.getResultValue())
                .unit(dto.getUnit())
                .referenceRange(dto.getReferenceRange())
                .status(LabResultStatus.PENDING)
                .testDate(LocalDateTime.now())
                .notes(dto.getNotes())
                .build();

        return toDto(labResultRepository.save(lr));
    }

    public List<LabResultDto> findByPatientId(Long patientId) {
        // [A01] Nema provere da li je pozivalac taj pacijent ili njegov doktor
        return labResultRepository.findByPatientId(patientId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    private LabResultDto toDto(LabResult lr) {
        return LabResultDto.builder()
                .id(lr.getId())
                .patientId(lr.getPatient().getId())
                .patientName(lr.getPatient().getUser().getUsername())
                .labTechId(lr.getLabTech().getId())
                .testName(lr.getTestName())
                .resultValue(lr.getResultValue())
                .unit(lr.getUnit())
                .referenceRange(lr.getReferenceRange())
                .status(lr.getStatus())
                .resultDate(lr.getTestDate())
                .notes(lr.getNotes())
                .attachmentPath(lr.getAttachmentPath())
                .build();
    }
}
