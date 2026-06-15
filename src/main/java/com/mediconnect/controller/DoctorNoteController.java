package com.mediconnect.controller;

import com.mediconnect.dto.ClinicalNoteDto;
import com.mediconnect.service.DoctorNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// Module B — Clinical Notes (Doctor View Redesign Phase 2).
//
// [A01] No role check / no @PreAuthorize. SecurityConfig.permitAll() reaches
//        every endpoint here. Modules B's primary OWASP focus is A03 (template
//        engine + XML parser) and A08 (JWT alg=none + in-place overwrite).
@RestController
@RequestMapping("/api/doctor/notes")
@RequiredArgsConstructor
public class DoctorNoteController {

    private final DoctorNoteService service;

    @GetMapping
    public ResponseEntity<List<ClinicalNoteDto>> list(@RequestParam(required = false) Long patientId) {
        return ResponseEntity.ok(service.list(patientId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicalNoteDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    // [A03] templateName / templateBody flow into Freemarker — see service.
    @PostMapping
    public ResponseEntity<ClinicalNoteDto> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    // [A08] In-place overwrite — no version, no history.
    @PutMapping("/{id}")
    public ResponseEntity<ClinicalNoteDto> update(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.update(id, body));
    }

    // [A09] Hard delete with no audit row.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // [A03] DocumentBuilderFactory with defaults — XXE reachable from any caller.
    @PostMapping("/import")
    public ResponseEntity<ClinicalNoteDto> importXml(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "patientId", required = false) Long patientId) {
        return ResponseEntity.ok(service.importXml(file, patientId));
    }

    // [A08] JWT alg=none accepted — see JwtNoneVerifier.
    @PostMapping("/{id}/co-sign")
    public ResponseEntity<ClinicalNoteDto> coSign(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.coSign(id, body));
    }
}
