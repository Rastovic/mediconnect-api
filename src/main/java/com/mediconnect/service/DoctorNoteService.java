package com.mediconnect.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediconnect.dto.ClinicalNoteDto;
import com.mediconnect.entity.*;
import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorNoteService {

    private final ClinicalNoteRepository noteRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final TemplateRenderer templateRenderer;
    private final JwtNoneVerifier jwtVerifier;

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ClinicalNoteDto> list(Long patientId) {
        List<ClinicalNote> notes = patientId == null
                ? noteRepository.findAllByOrderByCreatedAtDesc()
                : noteRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        return notes.stream().map(this::toDto).collect(Collectors.toList());
    }

    public ClinicalNoteDto get(Long id) {
        return toDto(noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note not found: " + id)));
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/notes
    // -------------------------------------------------------------------------
    //
    // [A03] templateName + (optional) templateBody both flow into Freemarker.
    //        templateName resolves to a filesystem path under
    //        notes/templates/${templateName}.ftl — caller can traverse out
    //        of the directory. templateBody is rendered inline so an SSTI
    //        payload runs directly.
    // [A05] Data map keys/values reach the model untouched — `<script>` or
    //        `<img onerror>` in any value lands in the rendered HTML.
    // [A01] No "is this my patient" check, no doctor-id check.
    public ClinicalNoteDto create(Map<String, Object> body) {
        Long patientId = numericId(body, "patientId");
        Long doctorId  = numericId(body, "doctorId");
        String templateName = body.get("templateName") == null ? "soap" : body.get("templateName").toString();
        String templateBody = body.get("templateBody") == null ? null : body.get("templateBody").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = body.get("data") instanceof Map
                ? (Map<String, Object>) body.get("data")
                : new HashMap<>();

        String html;
        if (templateBody != null && !templateBody.isBlank()) {
            // [A03] Inline SSTI sink.
            html = templateRenderer.renderInline(templateBody, data);
        } else {
            // [A03] Path traversal via templateName.
            html = templateRenderer.renderByName(templateName, data);
        }

        Patient patient = patientId == null ? null
                : patientRepository.findById(patientId).orElse(null);
        Doctor doctor = doctorId == null ? null
                : doctorRepository.findById(doctorId).orElse(null);

        ClinicalNote note = ClinicalNote.builder()
                .patient(patient)
                .doctor(doctor)
                .templateName(templateName)
                .rawData(serialiseQuietly(data))
                .renderedHtml(html)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return toDto(noteRepository.save(note));
    }

    // -------------------------------------------------------------------------
    // PUT /api/doctor/notes/{id}
    // -------------------------------------------------------------------------
    //
    // [A08] Overwrites in place — no history table, no version column,
    //        no diff capture. The previous renderedHtml/rawData/template
    //        are silently discarded.
    // [A09] No audit row written for the edit.
    public ClinicalNoteDto update(Long id, Map<String, Object> body) {
        ClinicalNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note not found: " + id));
        if (body.containsKey("templateName")) note.setTemplateName(body.get("templateName").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = body.get("data") instanceof Map
                ? (Map<String, Object>) body.get("data") : null;
        if (data != null) note.setRawData(serialiseQuietly(data));
        if (body.containsKey("renderedHtml")) {
            // [A05] Caller-supplied HTML stored verbatim — no template engine
            //        involvement here, just a direct overwrite.
            note.setRenderedHtml(body.get("renderedHtml").toString());
        } else if (data != null) {
            note.setRenderedHtml(templateRenderer.renderByName(note.getTemplateName(), data));
        }
        return toDto(noteRepository.save(note));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/doctor/notes/{id}
    // -------------------------------------------------------------------------
    //
    // [A09] Hard delete — no audit entry. Compounds the lack of revision
    //        history from PUT.
    public void delete(Long id) {
        noteRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/notes/import (multipart)
    // -------------------------------------------------------------------------
    //
    // [A03] DocumentBuilderFactory.newInstance() with defaults — external
    //        entities resolved. Classic XXE.
    //
    //        Demo payload (.xml upload):
    //          <?xml version="1.0"?>
    //          <!DOCTYPE n [<!ENTITY x SYSTEM "file:///etc/passwd">]>
    //          <note><patientId>1</patientId>
    //                <templateName>imported</templateName>
    //                <body>&x;</body>
    //          </note>
    //
    //        The contents of /etc/passwd land in renderedHtml, then in the
    //        Doctor Note detail page DOM.
    public ClinicalNoteDto importXml(MultipartFile file, Long patientId) {
        try {
            // Harden the parser against XXE: forbid DOCTYPE entirely, and
            // disable external general/parameter entities and external DTD
            // loading as defence in depth.
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new ByteArrayInputStream(file.getBytes())));

            String templateName = text(doc, "templateName", "imported");
            String body = text(doc, "body", "");
            Long pid = patientId != null ? patientId : longOrNull(text(doc, "patientId", null));

            Patient patient = pid == null ? null : patientRepository.findById(pid).orElse(null);
            String html;
            if (body != null && !body.isBlank()) {
                // [A05] Body from the uploaded XML stored as-is and rendered.
                html = body;
            } else {
                html = templateRenderer.renderByName(templateName, Map.of());
            }
            ClinicalNote note = ClinicalNote.builder()
                    .patient(patient)
                    .templateName(templateName)
                    .rawData(new String(file.getBytes()))
                    .renderedHtml(html)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return toDto(noteRepository.save(note));
        } catch (Exception e) {
            // Generic message to the caller; details stay server-side.
            throw new RuntimeException("XML import failed");
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/notes/{id}/co-sign
    // -------------------------------------------------------------------------
    //
    // [A08] JWT verified by JwtNoneVerifier — alg=none accepted, signature
    //        ignored even when present. The `sub` claim becomes the signer.
    // [A07] No correlation to the JWT used for the request itself; co-signer
    //        identity wholly client-asserted.
    public ClinicalNoteDto coSign(Long id, Map<String, Object> body) {
        ClinicalNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note not found: " + id));
        String jwt = body.get("jwt") == null ? null : body.get("jwt").toString();
        String sub = jwtVerifier.extractSubjectUnsafe(jwt);
        if (sub == null) {
            throw new RuntimeException("Invalid co-signer credentials");
        }
        note.setSignature(jwt);
        note.setSignerUsername(sub);
        return toDto(noteRepository.save(note));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long numericId(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Long longOrNull(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    private String text(Document doc, String tag, String fallback) {
        NodeList nl = doc.getElementsByTagName(tag);
        if (nl.getLength() == 0) return fallback;
        return nl.item(0).getTextContent();
    }

    private String serialiseQuietly(Object data) {
        try { return mapper.writeValueAsString(data); }
        catch (Exception e) { return null; }
    }

    private ClinicalNoteDto toDto(ClinicalNote n) {
        Patient pat = n.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = n.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        Map<String, Object> data = null;
        if (n.getRawData() != null) {
            try { data = mapper.readValue(n.getRawData(), new TypeReference<Map<String, Object>>() {}); }
            catch (Exception e) { /* leave null */ }
        }
        return ClinicalNoteDto.builder()
                .id(n.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorId(doc == null ? null : doc.getId())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .templateName(n.getTemplateName())
                .data(data)
                .rawData(n.getRawData())
                .renderedHtml(n.getRenderedHtml())
                .signature(n.getSignature())
                .signerUsername(n.getSignerUsername())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }
}
