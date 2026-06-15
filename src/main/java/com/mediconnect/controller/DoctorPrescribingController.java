package com.mediconnect.controller;

import com.mediconnect.dto.PrescriptionDto;
import com.mediconnect.dto.PrescriptionSignatureDto;
import com.mediconnect.service.DoctorPrescribingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Module D — E-Prescribing & Drug Safety (Doctor View Redesign Phase 4).
//
// [A01] No role check, no @PreAuthorize. SecurityConfig.permitAll() covers
//        every endpoint. Module D's primary focus is A08 (MD5 + unsigned PDF
//        + alg=none co-sign) and A03 (SSRF via pharmacyCallbackUrl + Nashorn
//        eval RCE via catalogueUrl).
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorPrescribingController {

    private final DoctorPrescribingService service;

    // [A03] pharmacyCallbackUrl fetched server-side (SSRF + data exfil).
    @PostMapping("/prescriptions")
    public ResponseEntity<PrescriptionDto> create(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.create(body));
    }

    // [A08] MD5 + hardcoded key signature; key disclosed in response.
    @PostMapping("/prescriptions/{id}/sign")
    public ResponseEntity<PrescriptionSignatureDto> sign(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(service.sign(id));
    }

    // [A08] Unsigned PDF (no PKCS#7 wrapper, no certificate chain).
    @GetMapping("/prescriptions/{id}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable Long id) {
        byte[] pdf = service.generatePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "prescription-" + id + ".pdf");
        return new ResponseEntity<>(new ByteArrayResource(pdf), headers, 200);
    }

    // [A03] SSRF on catalogueUrl + [A05] Nashorn eval RCE on response body.
    @PostMapping("/drug-interactions/check")
    public ResponseEntity<Map<String, Object>> drugInteractions(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.drugInteractions(body));
    }

    // [A08] JWT alg=none accepted; sub claim trusted.
    @PostMapping("/prescriptions/{id}/co-sign")
    public ResponseEntity<PrescriptionDto> coSign(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.coSign(id, body));
    }
}
