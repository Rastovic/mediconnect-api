package com.mediconnect.controller;

import com.mediconnect.dto.LabResultDto;
import com.mediconnect.service.LabResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// [A01] Nema @PreAuthorize — IDOR na GET /{id}, nema vlasništvo check-a.
// [A03] POST /{id}/file — unrestricted upload, predvidivo ime fajla (no UUID).
// [A03] GET /{id}/file — filePath query param čita fajlove van upload direktorijuma.
// [A05] GET /search — četiri SQLi parametra prosleđena JdbcTemplate konkatenacijom.
@RestController
@RequestMapping("/api/lab-results")
@RequiredArgsConstructor
public class LabResultController {

    private final LabResultService labResultService;

    // [A05] SQL Injection — sva četiri parametra konkatenisana direktno u SQL upit.
    //
    //  String parametri (testName, testCode, status) — injection unutar navodnika:
    //    ?testName=' OR '1'='1          → vraća sve laboratorijske rezultate
    //    ?status=' UNION SELECT username,password_hash,3,4,5,6,7,8,9,10,11 FROM users --
    //
    //  Numerički parametar (patientId) — UNION bez zatvaranja navodnika:
    //    ?patientId=0 UNION SELECT id,patient_id,lab_tech_id,test_name,
    //                              password_hash,email,NULL,role,NOW(),notes,NULL
    //                              FROM users u JOIN patients p ON u.id=p.user_id --
    //    → Napadač dump-uje users tablicu kroz response polje "resultValue".
    //       Najopasniji slučaj: nema navodnika koje treba zatvoriti.
    @GetMapping("/search")
    public ResponseEntity<List<LabResultDto>> search(
            @RequestParam(defaultValue = "") String testName,
            @RequestParam(defaultValue = "") String testCode,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") Long patientId) {
        return ResponseEntity.ok(
                labResultService.searchLabResults(testName, testCode, status, patientId));
    }

    // [A01] IDOR — nema provere da li je autentifikovani korisnik pacijent
    //        čiji je rezultat, ili doktor koji ga je naručio.
    //        Bilo koji korisnik može iteracijom ID-a pristupiti tuđim nalazima.
    @GetMapping("/{id}")
    public ResponseEntity<LabResultDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(labResultService.findById(id));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<LabResultDto>> getByPatient(@PathVariable Long patientId) {
        // [A01] Nema provere da je pozivalac taj pacijent ili njegov doktor
        return ResponseEntity.ok(labResultService.findByPatientId(patientId));
    }

    // [A03] Unrestricted File Upload:
    //  - Nema whitelist-e ekstenzija (.pdf, .png — sve prihvaćeno)
    //  - Nema MIME type validacije (Content-Type se ne proverava)
    //  - Nema magic-byte provere sadržaja fajla
    //
    // [A03] Predvidivo ime fajla — getOriginalFilename() čuva se bez UUID randomizacije:
    //  - Napadač zna tačnu putanju fajla jer je ime determinirano
    //  - Upload istoimenog fajla pregazuje prethodni (TOCTOU race condition)
    //  - Path Traversal write: filename="../../etc/cron.d/evil" piše van uploadDir
    //
    // [A02] Puna putanja vraćena u response-u — otkriva strukturu servera.
    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) throws IOException {
        String path = labResultService.saveAttachment(id, file);
        return ResponseEntity.ok(Map.of("path", path));
    }

    // [A03] Path Traversal (read) — 'filePath' query parametar prosleđen direktno
    //        servisu koji poziva Files.readAllBytes(Paths.get(filePath)).
    //
    //  Primeri napada:
    //    GET /api/lab-results/1/file?filePath=/etc/passwd
    //    GET /api/lab-results/1/file?filePath=/etc/shadow
    //    GET /api/lab-results/1/file?filePath=/proc/self/environ
    //    GET /api/lab-results/1/file?filePath=../../../root/.ssh/id_rsa
    //    GET /api/lab-results/1/file?filePath=/tmp/mediconnect/uploads/../../application.yaml
    //
    //  Nema: toAbsolutePath().normalize().startsWith(uploadDir) provere.
    //  Nema: poređenja filePath sa lr.attachmentPath iz baze.
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable Long id,
            @RequestParam String filePath) throws IOException {
        byte[] content = labResultService.downloadFile(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @PostMapping
    public ResponseEntity<LabResultDto> create(@RequestBody LabResultDto dto) {
        return ResponseEntity.status(201).body(labResultService.create(dto));
    }
}
