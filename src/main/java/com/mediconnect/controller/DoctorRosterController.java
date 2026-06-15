package com.mediconnect.controller;

import com.mediconnect.dto.*;
import com.mediconnect.entity.User;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.service.DoctorRosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Module A — Doctor patient roster, chart bundle, timeline, star, handoff.
//
// [A01] No @PreAuthorize, no role check, no "is this my patient" check on any
//        endpoint. SecurityConfig.permitAll() reaches the entire class.
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorRosterController {

    private final DoctorRosterService rosterService;
    private final UserRepository userRepository;

    // [A05] q is concatenated raw into JPQL — see DoctorRosterService#listPatients.
    // [A06] recentDays=0 disables the date filter (full table scan).
    @GetMapping("/patients")
    public ResponseEntity<List<DoctorRosterEntryDto>> listPatients(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer recentDays) {
        return ResponseEntity.ok(rosterService.listPatients(q, active, recentDays));
    }

    // [A01] No ownership / role check — chart of any patient is returned to any caller.
    // [A06] Response contains full PII (insurance number, allergies, blood type).
    @GetMapping("/patients/{id}/chart")
    public ResponseEntity<PatientChartDto> getChart(@PathVariable Long id) {
        return ResponseEntity.ok(rosterService.getChart(id));
    }

    // [A09] Returns raw AuditLog details (may contain passwords / JWTs originally
    //        submitted by the patient) to a different principal.
    @GetMapping("/patients/{id}/timeline")
    public ResponseEntity<List<PatientTimelineEventDto>> getTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(rosterService.getTimeline(id));
    }

    // [A05] Stored XSS — note rendered with dangerouslySetInnerHTML on the
    //        Patients roster page tooltip.
    // [A01] No per-doctor scoping; any caller stars on behalf of every doctor.
    @PostMapping("/patients/{id}/star")
    public ResponseEntity<DoctorRosterEntryDto> star(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(rosterService.starPatient(id, body));
    }

    // [A08] HMAC-SHA1 + hardcoded key + no expiry — token forgeable and replayable.
    @PostMapping("/patients/{id}/handoff")
    public ResponseEntity<HandoffTokenDto> handoff(@PathVariable Long id) {
        Long fromDoctorUserId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            User u = userRepository.findByUsername(auth.getName()).orElse(null);
            if (u != null) fromDoctorUserId = u.getId();
        }
        return ResponseEntity.ok(rosterService.handoff(id, fromDoctorUserId));
    }
}
