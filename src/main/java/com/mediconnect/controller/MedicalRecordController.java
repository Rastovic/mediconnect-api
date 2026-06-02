package com.mediconnect.controller;

import com.mediconnect.dto.MedicalRecordDto;
import com.mediconnect.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// [A01] No @PreAuthorize — doctor identity never verified against patient assignment.
// [A03] File upload and download endpoints are Path Traversal and unrestricted upload vectors.
// [A08] No content_hash computed at upload, not verified at download.
@RestController
@RequestMapping("/api/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    // [A01] No access control — any caller receives all medical records
    @GetMapping
    public ResponseEntity<List<MedicalRecordDto>> getAllRecords() {
        return ResponseEntity.ok(medicalRecordService.findAll());
    }

    // [A01] Any authenticated DOCTOR (or unauthenticated caller, given permitAll)
    //        can create a medical record for ANY patient.
    //        No verification that the doctor has ever treated this patient,
    //        no shared appointment check, no care-plan membership check.
    @PostMapping
    public ResponseEntity<MedicalRecordDto> createRecord(@RequestBody MedicalRecordDto dto) {
        return ResponseEntity.status(201).body(medicalRecordService.create(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicalRecordDto> getRecord(@PathVariable Long id) {
        return ResponseEntity.ok(medicalRecordService.findById(id));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalRecordDto>> getByPatient(@PathVariable Long patientId) {
        // [A01] No check that the caller is the patient or their treating doctor
        return ResponseEntity.ok(medicalRecordService.findByPatientId(patientId));
    }

    // [A01] No ownership check — any authenticated user can update any medical record.
    //        No verification that the caller is the treating doctor or patient.
    @PutMapping("/{id}")
    public ResponseEntity<MedicalRecordDto> updateRecord(
            @PathVariable Long id,
            @RequestBody MedicalRecordDto dto) {
        return ResponseEntity.ok(medicalRecordService.update(id, dto));
    }

    // [A03] Unrestricted File Upload — no validation of:
    //        - file extension (accepts .php, .jsp, .exe, .sh, ...)
    //        - Content-Type / MIME type (accepts application/octet-stream, text/html, ...)
    //        - file size (no maximum enforced here)
    //        - file content (no magic-byte check)
    //
    // [A03] Path Traversal write — getOriginalFilename() is attacker-controlled.
    //        filename = "../../etc/cron.d/backdoor" writes outside uploadDir.
    //
    // [A08] No content_hash computed or stored — integrity of uploaded file is unverifiable.
    @PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file) throws IOException {
        String storedPath = medicalRecordService.uploadAttachment(id, file);
        // [A02] Full filesystem path returned to client — leaks server directory structure
        return ResponseEntity.ok(Map.of("path", storedPath));
    }

    // [A03] Path Traversal read — 'filePath' query parameter is used verbatim
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
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long id,
            @RequestParam String filePath) throws IOException {
        byte[] content = medicalRecordService.downloadAttachment(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
