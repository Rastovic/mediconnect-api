package com.mediconnect.controller;

import com.mediconnect.dto.MedicalRecordDto;
import com.mediconnect.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// [A01] No @PreAuthorize — doctor identity never verified against patient assignment.
// [A05] File upload and download endpoints are Path Traversal and unrestricted upload vectors.
// [A08] No content_hash computed at upload, not verified at download.
@RestController
@RequestMapping("/api/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    // Principal-scoped in the service: a PATIENT receives only their own records,
    // staff receive all.
    @GetMapping
    public ResponseEntity<List<MedicalRecordDto>> getAllRecords() {
        return ResponseEntity.ok(medicalRecordService.findAll());
    }

    // Only clinicians (doctors) and admins may author a medical record.
    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<MedicalRecordDto> createRecord(@RequestBody MedicalRecordDto dto) {
        return ResponseEntity.status(201).body(medicalRecordService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canViewMedicalRecord(authentication,#id)")
    public ResponseEntity<MedicalRecordDto> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(medicalRecordService.findById(id));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("@authz.canViewPatientRecords(authentication,#patientId)")
    public ResponseEntity<List<MedicalRecordDto>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(medicalRecordService.findByPatientId(patientId));
    }

    // Only the treating doctor on the record (or an admin) may edit it.
    @PutMapping("/{id}")
    @PreAuthorize("@authz.canEditMedicalRecord(authentication,#id)")
    public ResponseEntity<MedicalRecordDto> updateRecord(
            @PathVariable Long id,
            @RequestBody MedicalRecordDto dto) {
        return ResponseEntity.ok(medicalRecordService.update(id, dto));
    }

    // [A05] Unrestricted File Upload — no validation of:
    //        - file extension (accepts .php, .jsp, .exe, .sh, ...)
    //        - Content-Type / MIME type (accepts application/octet-stream, text/html, ...)
    //        - file size (no maximum enforced here)
    //        - file content (no magic-byte check)
    //
    // [A05] Path Traversal write — getOriginalFilename() is attacker-controlled.
    //        filename = "../../etc/cron.d/backdoor" writes outside uploadDir.
    //
    // [A08] No content_hash computed or stored — integrity of uploaded file is unverifiable.
    @PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) throws IOException {
        String storedPath = medicalRecordService.uploadAttachment(id, file);
        // [A04] Full filesystem path returned to client — leaks server directory structure
        return ResponseEntity.ok(Map.of("path", storedPath));
    }

    // [A05] Path Traversal read — 'filePath' query parameter is used verbatim
    //        to read a file from the filesystem. No canonical path check,
    //        no boundary enforcement against the upload directory.
    //
    //        Attack examples:
    //          GET /api/medical-records/1/attachment?filePath=/etc/passwd
    //          GET /api/medical-records/1/attachment?filePath=/etc/shadow
    //          GET /api/medical-records/1/attachment?filePath=../../../root/.ssh/id_rsa
    //          GET /api/medical-records/1/attachment?filePath=/proc/self/environ
    //
    // [A08] No hash verification — the file returned could have been silently modified;
    //        client cannot check integrity against a stored content_hash.
    @GetMapping("/{id}/attachment")
    @PreAuthorize("@authz.canViewMedicalRecord(authentication,#id)")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id) throws IOException {
        byte[] content = medicalRecordService.downloadAttachment(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment")
                .body(content);
    }
}
