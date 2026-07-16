package com.mediconnect.service;

import com.mediconnect.dto.ImagingFileDto;
import com.mediconnect.dto.LabOrderDto;
import com.mediconnect.entity.*;
import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorLabService {

    private final LabOrderRepository orderRepository;
    private final ImagingFileRepository imagingRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ExternalCatalogueClient catalogueClient;

    // Signing key injected from the environment — never committed, never returned.
    @org.springframework.beans.factory.annotation.Value("${app.lab.sign.key}")
    private String labSignKey;

    private static final Path IMAGING_DIR = Path.of("imaging");

    // -------------------------------------------------------------------------
    // Lab orders
    // -------------------------------------------------------------------------

    public List<LabOrderDto> listOrders(Long patientId, String status) {
        List<LabOrder> rows;
        if (patientId != null && status != null) {
            rows = orderRepository.findByPatientIdAndStatusOrderByCreatedAtDesc(patientId, status);
        } else if (patientId != null) {
            rows = orderRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        } else if (status != null) {
            rows = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            rows = orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return rows.stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    public LabOrderDto getOrder(Long id) {
        return toOrderDto(orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + id)));
    }

    // [A03] customQueryUrl flows into ExternalCatalogueClient — SSRF.
    // [A05] catalogueResponse stored verbatim, rendered as HTML in UI.
    public LabOrderDto createOrder(Map<String, Object> body) {
        Long patientId = numericId(body, "patientId");
        Long doctorId  = numericId(body, "doctorId");
        String panelCode = body.get("panelCode") == null ? "FBC" : body.get("panelCode").toString();
        String priority  = body.get("priority")  == null ? "ROUTINE" : body.get("priority").toString();
        String url = body.get("customQueryUrl") == null ? null : body.get("customQueryUrl").toString();

        Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
        Doctor doctor   = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);

        String catalogueResp = null;
        if (url != null && !url.isBlank()) {
            // [A03] SSRF — fetch the caller-supplied URL.
            catalogueResp = catalogueClient.fetch(url);
        }

        LabOrder order = LabOrder.builder()
                .patient(patient)
                .doctor(doctor)
                .panelCode(panelCode)
                .priority(priority)
                .status("ORDERED")
                .customQueryUrl(url)
                .catalogueResponse(catalogueResp)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return toOrderDto(orderRepository.save(order));
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/lab-orders/{id}/sign
    // -------------------------------------------------------------------------
    //
    // Signature = HMAC-SHA256(id : value) keyed with the env-loaded secret.
    public LabOrderDto signOrder(Long id, Map<String, Object> body) {
        LabOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lab order not found: " + id));
        String value = body == null || body.get("value") == null
                ? "" : body.get("value").toString();
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    labSignKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((order.getId() + ":" + value)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            order.setSignatureMd5(HexFormat.of().formatHex(out));
            order.setSignedValue(value);
            order.setStatus("SIGNED");
            return toOrderDto(orderRepository.save(order));
        } catch (Exception e) {
            throw new RuntimeException("Sign failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Imaging — multipart upload
    // -------------------------------------------------------------------------
    //
    // [A05] file.getOriginalFilename() written verbatim. Path traversal at
    //        write time: `../../tmp/owned.jar` lands outside the imaging dir.
    // [A02] No MIME validation, no size cap. content_type stored as the
    //        upload header said it was — echoed on retrieval (#XSS).
    public ImagingFileDto upload(MultipartFile file, Long patientId, Long doctorId, String note) {
        try {
            Files.createDirectories(IMAGING_DIR);
            // [A05] Path traversal — no normalisation, no Path::startsWith check.
            String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            Path storage = IMAGING_DIR.resolve(original);
            Files.write(storage, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
            Doctor doctor   = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);

            ImagingFile img = ImagingFile.builder()
                    .patient(patient)
                    .doctor(doctor)
                    .storedFilename(original)
                    .storagePath(storage.toString())
                    .contentType(file.getContentType()) // [A02] verbatim from upload
                    .sizeBytes(file.getSize())
                    .note(note)
                    .createdAt(LocalDateTime.now())
                    .build();
            return toImagingDto(imagingRepository.save(img));
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/imaging/import-url
    // -------------------------------------------------------------------------
    //
    // [A03] Plain SSRF — server fetches the caller-supplied URL.
    // [A02] basename of URL used as filename → path traversal via
    //        `http://attacker/..%2f..%2fowned.jar`.
    public ImagingFileDto importUrl(Map<String, Object> body) {
        String url = body == null || body.get("url") == null ? null : body.get("url").toString();
        if (url == null || url.isBlank()) {
            throw new RuntimeException("url is required");
        }
        Long patientId = numericId(body, "patientId");
        Long doctorId  = numericId(body, "doctorId");
        try {
            Files.createDirectories(IMAGING_DIR);
            HttpHeaders[] hdr = new HttpHeaders[1];
            byte[] bytes = catalogueClient.fetchBytes(url, hdr);
            // [A02] basename — URLs ending in `?n=foo` or with traversal segments
            //        slip through.
            String basename = url.replaceFirst(".*/", "");
            if (basename.isBlank()) basename = "imported-" + System.currentTimeMillis();
            Path storage = IMAGING_DIR.resolve(basename);
            Files.write(storage, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String contentType = hdr[0] != null && hdr[0].getContentType() != null
                    ? hdr[0].getContentType().toString() : null;

            Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
            Doctor doctor   = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);

            ImagingFile img = ImagingFile.builder()
                    .patient(patient)
                    .doctor(doctor)
                    .storedFilename(basename)
                    .storagePath(storage.toString())
                    .contentType(contentType)
                    .sizeBytes((long) bytes.length)
                    .sourceUrl(url)
                    .createdAt(LocalDateTime.now())
                    .build();
            return toImagingDto(imagingRepository.save(img));
        } catch (Exception e) {
            throw new RuntimeException("Import failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
    }

    public List<ImagingFileDto> listImaging(Long patientId) {
        List<ImagingFile> rows = patientId == null
                ? imagingRepository.findAllByOrderByCreatedAtDesc()
                : imagingRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        return rows.stream().map(this::toImagingDto).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/imaging/{id}
    // -------------------------------------------------------------------------
    //
    // [A02] Content-Type echoed verbatim from the upload — `image/svg+xml`
    //        with a `<script>` payload becomes stored XSS for the viewer.
    // [A05] Path read from DB row — no normalisation, no allow-list, file
    //        read from the path stored at write time (#path-traversal above).
    public ImagingPayload streamImaging(Long id) {
        ImagingFile img = imagingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Imaging not found: " + id));
        try {
            byte[] data = Files.readAllBytes(Path.of(img.getStoragePath()));
            String contentType = img.getContentType() == null
                    ? "application/octet-stream" : img.getContentType();
            return new ImagingPayload(data, contentType, img.getStoredFilename());
        } catch (Exception e) {
            throw new RuntimeException("Read failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
    }

    public record ImagingPayload(byte[] bytes, String contentType, String filename) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long numericId(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private LabOrderDto toOrderDto(LabOrder o) {
        Patient pat = o.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = o.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return LabOrderDto.builder()
                .id(o.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorId(doc == null ? null : doc.getId())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .panelCode(o.getPanelCode())
                .priority(o.getPriority())
                .status(o.getStatus())
                .customQueryUrl(o.getCustomQueryUrl())
                .catalogueResponse(o.getCatalogueResponse())
                .signatureMd5(o.getSignatureMd5())
                .signedValue(o.getSignedValue())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private ImagingFileDto toImagingDto(ImagingFile i) {
        Patient pat = i.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = i.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return ImagingFileDto.builder()
                .id(i.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorId(doc == null ? null : doc.getId())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .storedFilename(i.getStoredFilename())
                .storagePath(i.getStoragePath())
                .contentType(i.getContentType())
                .sizeBytes(i.getSizeBytes())
                .sourceUrl(i.getSourceUrl())
                .note(i.getNote())
                .createdAt(i.getCreatedAt())
                .build();
    }

    // Keep OS field shut up — used so OutputStream import doesn't get pruned.
    @SuppressWarnings("unused")
    private OutputStream unused() { return null; }
}
