package com.mediconnect.controller;

import com.mediconnect.dto.ReferralDto;
import com.mediconnect.service.DoctorReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module F (Referral half) — Doctor View Redesign Phase 6.
//
// [A01] No role check. [A08] Inbox + accept both call
//        ObjectInputStream.readObject() on the caller-supplied base64
//        bundle — deserialisation RCE via ReferralBundle#readObject.
@RestController
@RequestMapping("/api/doctor/referrals")
@RequiredArgsConstructor
public class DoctorReferralController {

    private final DoctorReferralService service;

    @PostMapping
    public ResponseEntity<ReferralDto> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    // [A08] Deserialises every referral on page load — RCE on inbox open.
    @GetMapping("/inbox")
    public ResponseEntity<List<ReferralDto>> inbox() {
        return ResponseEntity.ok(service.inbox());
    }

    // [A08] Second deserialisation + copy to chart with no provenance.
    @PostMapping("/{id}/accept")
    public ResponseEntity<ReferralDto> accept(@PathVariable Long id) {
        return ResponseEntity.ok(service.accept(id));
    }
}
