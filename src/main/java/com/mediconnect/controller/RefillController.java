package com.mediconnect.controller;

import com.mediconnect.dto.RefillCreateDto;
import com.mediconnect.dto.RefillRequestDto;
import com.mediconnect.service.RefillQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// [A10] Mishandling of Exceptional Conditions — Async Refill Queue REST surface.
//        Every endpoint here either triggers, exposes, or amplifies one of the
//        error-path defects in RefillQueueService.
//
// [A01] All endpoints sit under /api/refills/** which SecurityConfig maps to
//        permitAll() — no authentication or role enforcement required.
@RestController
@RequestMapping("/api/refills")
@RequiredArgsConstructor
public class RefillController {

    private final RefillQueueService refills;

    // [A01] No access control — returns every refill row, including failureReason
    //        (CWE-209 leak) and tempSlipPath (CWE-460 / A09 FS path disclosure).
    @GetMapping
    public ResponseEntity<List<RefillRequestDto>> getAll() {
        return ResponseEntity.ok(refills.list());
    }

    // [A01] No ownership check — any caller reads any single refill row.
    @GetMapping("/{id}")
    public ResponseEntity<RefillRequestDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(refills.findById(id));
    }

    // [A01] No ownership check — returns refills for any patient id.
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<RefillRequestDto>> getByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(refills.listForPatient(patientId));
    }

    // [A10] CWE-754 — accepts `quantity: null` from the request body. The DTO field
    //        is a boxed Integer specifically so null deserialises cleanly; the
    //        downstream validator then NPEs and the fail-open catch promotes the
    //        refill to READY without ever checking eligibility.
    //
    // [A07] requestedBy is taken from the request body — caller-attributed actor.
    @PostMapping
    public ResponseEntity<RefillRequestDto> create(@RequestBody RefillCreateDto dto) {
        return ResponseEntity.status(201).body(refills.enqueue(dto));
    }

    // [A10] CWE-362 — frontend "Force Concurrent Dispense" button fires 10 of these
    //        in parallel against the same id. Service has no row-level lock or
    //        version column, so both calls succeed and inventory decrements twice.
    //
    // [A07] pharmacistId arrives in the request body and is written into the audit
    //        trail without verification against the JWT.
    @PostMapping("/{id}/dispense")
    public ResponseEntity<RefillRequestDto> dispense(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long pharmacistId = body.get("pharmacistId");
        return ResponseEntity.ok(refills.dispense(id, pharmacistId));
    }

    // [A10] CWE-400 — no max-retry guard. A `while true` loop from curl exhausts
    //        the server's temp directory through SlipPrinter (CWE-460 amplifier).
    @PostMapping("/{id}/retry")
    public ResponseEntity<RefillRequestDto> retry(@PathVariable Long id) {
        return ResponseEntity.ok(refills.retry(id));
    }

    // [A01] No ownership check — any caller can delete any refill row.
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        refills.delete(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
