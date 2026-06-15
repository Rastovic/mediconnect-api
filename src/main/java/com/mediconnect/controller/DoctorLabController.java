package com.mediconnect.controller;

import com.mediconnect.dto.ImagingFileDto;
import com.mediconnect.dto.LabOrderDto;
import com.mediconnect.service.DoctorLabService;
import com.mediconnect.service.DoctorLabService.ImagingPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// Module C — Lab Orders & Imaging (Doctor View Redesign Phase 3).
//
// [A01] No role check on any endpoint. SecurityConfig.permitAll() reaches
//        every route. Module C's primary OWASP focus is A03 (SSRF via
//        customQueryUrl + import-url) and A08 (MD5 lab-order signature).
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorLabController {

    private final DoctorLabService service;

    // ---------- Lab orders ----------

    @GetMapping("/lab-orders")
    public ResponseEntity<List<LabOrderDto>> listOrders(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long patientId) {
        return ResponseEntity.ok(service.listOrders(patientId, status));
    }

    @GetMapping("/lab-orders/{id}")
    public ResponseEntity<LabOrderDto> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(service.getOrder(id));
    }

    // [A03] customQueryUrl flows into RestTemplate — SSRF sink.
    @PostMapping("/lab-orders")
    public ResponseEntity<LabOrderDto> createOrder(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(service.createOrder(body));
    }

    // [A08] MD5 signature with hardcoded key.
    @PostMapping("/lab-orders/{id}/sign")
    public ResponseEntity<LabOrderDto> signOrder(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(service.signOrder(id, body));
    }

    // ---------- Imaging ----------

    @GetMapping("/imaging")
    public ResponseEntity<List<ImagingFileDto>> listImaging(@RequestParam(required = false) Long patientId) {
        return ResponseEntity.ok(service.listImaging(patientId));
    }

    // [A05] Filename written verbatim — path traversal vector.
    // [A02] content_type echoed verbatim from upload header.
    @PostMapping(value = "/imaging/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImagingFileDto> uploadImaging(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "patientId", required = false) Long patientId,
                                                        @RequestParam(value = "doctorId",  required = false) Long doctorId,
                                                        @RequestParam(value = "note",      required = false) String note) {
        return ResponseEntity.ok(service.upload(file, patientId, doctorId, note));
    }

    // [A03] Plain SSRF — caller-supplied URL fetched server-side.
    @PostMapping("/imaging/import-url")
    public ResponseEntity<ImagingFileDto> importImagingUrl(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.importUrl(body));
    }

    // [A02] Content-Type echoed back from DB; `image/svg+xml` becomes XSS.
    @GetMapping("/imaging/{id}")
    public ResponseEntity<Resource> getImaging(@PathVariable Long id) {
        ImagingPayload p = service.streamImaging(id);
        HttpHeaders headers = new HttpHeaders();
        // [A02] No `Content-Disposition: attachment` — browsers render the
        //        response inline. SVG / HTML uploads execute as the doctor.
        headers.add(HttpHeaders.CONTENT_TYPE, p.contentType());
        return new ResponseEntity<>(new ByteArrayResource(p.bytes()), headers, 200);
    }
}
