package com.mediconnect.controller;

import com.mediconnect.dto.MedicalRecordDto;
import com.mediconnect.service.DoctorAIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Module F (AI half) — Doctor View Redesign Phase 6.
//
// [A01] No role check, no @PreAuthorize. SecurityConfig.permitAll() covers
//        every endpoint. Module F focuses on A08 (LLM output stored as
//        authoritative MedicalRecord, deserialisation RCE in the referral
//        half) and A02 (API key leak via /model-info).
@RestController
@RequestMapping("/api/doctor/ai")
@RequiredArgsConstructor
public class DoctorAIController {

    private final DoctorAIService service;

    // [A03] modelUrl SSRF + outbound PHI exfil.
    @PostMapping("/suggest")
    public ResponseEntity<Map<String, Object>> suggest(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.suggest(body));
    }

    // [A08] LLM response stored as aiVerified=true MedicalRecord.
    @PostMapping("/summarize-record")
    public ResponseEntity<MedicalRecordDto> summarizeRecord(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.summarizeRecord(body));
    }

    // [A02] API key returned in clear.
    @GetMapping("/model-info")
    public ResponseEntity<Map<String, Object>> modelInfo() {
        return ResponseEntity.ok(service.modelInfo());
    }
}
