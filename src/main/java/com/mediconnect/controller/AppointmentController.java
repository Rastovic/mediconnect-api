package com.mediconnect.controller;

import com.mediconnect.dto.AppointmentDto;
import com.mediconnect.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// [A01] No @PreAuthorize or caller identity check on any endpoint.
// [A05] GET / accepts doctorName query param and passes it to a raw SQL concatenation.
// [A06] PUT /{id}/status writes any status value with no state machine enforcement.
// [A08] GET /{id}/pdf returns content without Content-MD5 or any integrity header.
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // [A05] SQL Injection via 'doctorName' query parameter.
    //        The value is concatenated directly into a JDBC query string in AppointmentService.
    //
    //        Example attacks:
    //          ?doctorName=' OR '1'='1                       → all appointments
    //          ?doctorName=' UNION SELECT username,password_hash,3,4,5,6,7 FROM users --
    //                                                        → user table dump
    //          ?doctorName='; DROP TABLE appointments; --    → table destruction
    @GetMapping
    public ResponseEntity<List<AppointmentDto>> searchAppointments(
            @RequestParam(required = false, defaultValue = "") String doctorName) {
        return ResponseEntity.ok(appointmentService.searchAppointments(doctorName));
    }

    // [A01] IDOR — no check that the authenticated caller is the patient or
    //        the doctor on this appointment. Any authenticated (or unauthenticated,
    //        given SecurityConfig.permitAll) user can retrieve any appointment by id.
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentDto> getAppointmentById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    // [A06] No state machine — any status string is accepted without validating
    //        the current state or the role of the caller.
    //        A patient can send {"status":"APPROVED"} and approve their own appointment.
    //        A billing attack: {"status":"COMPLETED"} on an already COMPLETED appointment
    //        re-triggers completion logic if any downstream listener relies on this event.
    @PutMapping("/{id}/status")
    public ResponseEntity<AppointmentDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(appointmentService.updateStatus(id, status));
    }

    // [A08] Software and Data Integrity Failure — PDF is served without:
    //        - Content-MD5 header  (RFC 1864 — allows receiver to detect corruption/tampering)
    //        - ETag based on content hash
    //        - Any digital signature inside the document
    //        A MITM attacker or compromised CDN can silently alter the PDF
    //        (change diagnosis, medication dosage, appointment date) and the
    //        client has no mechanism to detect the modification.
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdfBytes = appointmentService.generatePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "appointment-" + id + ".pdf");
        headers.setContentLength(pdfBytes.length);
        // [A08] Content-MD5 intentionally omitted — tampering is undetectable
        // Secure implementation would add:
        //   headers.set("Content-MD5", Base64.getEncoder().encodeToString(
        //       MessageDigest.getInstance("MD5").digest(pdfBytes)));

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    // [A01] IDOR — no ownership check. Any authenticated user can update any appointment.
    //        Only future appointments are allowed to be updated (date/notes).
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAppointment(
            @PathVariable Long id,
            @RequestBody AppointmentDto dto) {
        try {
            return ResponseEntity.ok(appointmentService.update(id, dto));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<AppointmentDto> createAppointment(@RequestBody AppointmentDto dto) {
        return ResponseEntity.status(201).body(appointmentService.create(dto));
    }
}
