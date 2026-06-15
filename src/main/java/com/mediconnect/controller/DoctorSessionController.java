package com.mediconnect.controller;

import com.mediconnect.dto.TelemedicineSessionDto;
import com.mediconnect.service.DoctorSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module E — Telemedicine Sessions (Doctor View Redesign Phase 5).
//
// [A01] No role check, no @PreAuthorize. SecurityConfig.permitAll() covers
//        every endpoint. Module E's primary focus is A02 (plaintext token in
//        URL + unauthenticated iCal feed leaking PHI) and A03 (recording URL
//        SSRF).
@RestController
@RequestMapping("/api/doctor/sessions")
@RequiredArgsConstructor
public class DoctorSessionController {

    private final DoctorSessionService service;

    @GetMapping
    public ResponseEntity<List<TelemedicineSessionDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<TelemedicineSessionDto> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    // [A01] Returns the join token in clear to any caller.
    @GetMapping("/{id}")
    public ResponseEntity<TelemedicineSessionDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<TelemedicineSessionDto> end(@PathVariable Long id) {
        return ResponseEntity.ok(service.end(id));
    }

    // [A03] SSRF on recordingUrl + [A02] basename path traversal.
    @PostMapping("/{id}/recording")
    public ResponseEntity<TelemedicineSessionDto> recording(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.attachRecording(id, body));
    }

    // [A02] Public iCal feed — no auth required, ?doctorId enumerable.
    //        SUMMARY exposes patient name + reason for visit (PHI).
    @GetMapping(value = "/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> calendarFeed(@RequestParam(required = false) Long doctorId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar"))
                .body(service.icalFeed(doctorId));
    }
}
