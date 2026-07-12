package com.mediconnect.controller;

import com.mediconnect.dto.AppointmentRowDto;
import com.mediconnect.dto.ConflictPairDto;
import com.mediconnect.service.DoctorAppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// Module G — Doctor Appointments Tab (replaces removed AI Assist).
//
// [A01] No role check on any endpoint. SecurityConfig.permitAll() reaches
//        every route. Primary OWASP focus: A06 (no double-book / no rate
//        limit / no row cap), A07 (actorDoctorId from body), A03 (CSV
//        formula injection on export + unbounded CSV import).
@RestController
@RequestMapping("/api/doctor/appointments")
@RequiredArgsConstructor
public class DoctorAppointmentController {

    private final DoctorAppointmentService service;

    // [A05] q concatenated into raw SQL.
    @GetMapping
    public ResponseEntity<List<AppointmentRowDto>> list(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.list(doctorId, status, from, to, q));
    }

    @GetMapping("/today")
    public ResponseEntity<List<AppointmentRowDto>> today(@RequestParam(required = false) Long doctorId) {
        return ResponseEntity.ok(service.today(doctorId));
    }

    // [A01] Returns every doctor's conflicts when no filter supplied.
    @GetMapping("/conflicts")
    public ResponseEntity<List<ConflictPairDto>> conflicts(@RequestParam(required = false) Long doctorId) {
        return ResponseEntity.ok(service.conflicts(doctorId));
    }

    // [A06] No double-book check. [A07] actorDoctorId from body.
    @PostMapping("/{id}/approve")
    public ResponseEntity<AppointmentRowDto> approve(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(service.approve(id, body));
    }

    // [A05] declineReason rendered as HTML in the inbox.
    @PostMapping("/{id}/decline")
    public ResponseEntity<AppointmentRowDto> decline(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(service.decline(id, body));
    }

    // [A08] Overwrites scheduledAt in place.
    @PostMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentRowDto> reschedule(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.reschedule(id, body));
    }

    // [A08] Auto-creates a MedicalRecord with caller-supplied notes as diagnosis.
    @PostMapping("/{id}/complete")
    public ResponseEntity<AppointmentRowDto> complete(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(service.complete(id, body));
    }

    // [A06] No rate limit.
    @PostMapping("/{id}/no-show")
    public ResponseEntity<AppointmentRowDto> noShow(@PathVariable Long id) {
        return ResponseEntity.ok(service.noShow(id));
    }

    // [A06][A09] Unbounded ids list; single audit row covers N mutations.
    @PostMapping("/bulk-status")
    public ResponseEntity<Map<String, Object>> bulkStatus(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.bulkStatus(body));
    }

    // [A06] No row cap → DoS.
    // [A03] notes cells stored verbatim; surfaces in export as formula.
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importCsv(file));
    }

    // [A03] CSV formula injection — =cmd|'/c calc'!A1 on Excel open.
    // [A02] Served inline (no Content-Disposition: attachment).
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@RequestParam(required = false) Long doctorId) {
        String csv = service.exportCsv(doctorId);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("text/csv"));
        // [A02] No Content-Disposition — opens inline in browser, caches it.
        return new ResponseEntity<>(csv, h, 200);
    }
}
