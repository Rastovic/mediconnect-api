package com.mediconnect.service;

import com.mediconnect.dto.MedicalRecordDto;
import com.mediconnect.entity.*;
import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorAIService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ExternalCatalogueClient catalogueClient;

    // [A02][A04] Hardcoded AI gateway URL + API key — checked into source.
    //        Returned in clear by GET /ai/model-info.
    public static final String AI_GATEWAY_URL = "https://api.openai.example.com/v1/chat/completions";
    public static final String AI_API_KEY     = "sk-mediconnect-prod-OZk1JxFhvE2pXt9rW3aB7Cq";
    public static final String AI_MODEL       = "gpt-medi-clinical-v1";

    // -------------------------------------------------------------------------
    // POST /api/doctor/ai/suggest
    // -------------------------------------------------------------------------
    //
    // [A03] modelUrl is caller-supplied. Server POSTs the patient chart payload
    //        to that URL (SSRF + outbound PHI exfil). The fetched response is
    //        returned to the caller.
    // [A02] If modelUrl missing, falls back to the hardcoded AI_GATEWAY_URL
    //        and still includes AI_API_KEY in the outbound Authorization
    //        header — so even the no-SSRF path leaks the prod key on the
    //        outbound channel.
    public Map<String, Object> suggest(Map<String, Object> body) {
        Long patientId = numericId(body, "patientId");
        String modelUrl = body == null || body.get("modelUrl") == null
                ? AI_GATEWAY_URL : body.get("modelUrl").toString();
        String prompt = body == null || body.get("prompt") == null
                ? "" : body.get("prompt").toString();
        Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);

        StringBuilder payload = new StringBuilder();
        payload.append("{\"model\":\"").append(AI_MODEL).append("\",")
               .append("\"prompt\":\"").append(escape(prompt)).append("\",");
        if (patient != null && patient.getUser() != null) {
            User u = patient.getUser();
            payload.append("\"patient\":{\"id\":").append(patient.getId())
                   .append(",\"name\":\"").append(escape(u.getFirstName() + " " + u.getLastName())).append("\"")
                   .append(",\"dob\":\"").append(patient.getDateOfBirth()).append("\"")
                   .append(",\"allergies\":\"").append(escape(patient.getAllergies())).append("\"")
                   .append(",\"bloodType\":\"").append(escape(patient.getBloodType())).append("\"}");
        } else {
            payload.append("\"patient\":null");
        }
        payload.append("}");

        return postJsonAndReadResponse(modelUrl, payload.toString(), body);
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/ai/summarize-record
    // -------------------------------------------------------------------------
    //
    // [A08] Response from the external LLM is stored verbatim as a new
    //        MedicalRecord row flagged `aiVerified=true`. Downstream chart
    //        consumers treat the row as authoritative — no provenance check,
    //        no signature, no human review.
    // [A03] modelUrl SSRF identical to #suggest.
    public MedicalRecordDto summarizeRecord(Map<String, Object> body) {
        Long recordId = numericId(body, "recordId");
        Long patientId = numericId(body, "patientId");
        Long doctorId = numericId(body, "doctorId");
        String modelUrl = body == null || body.get("modelUrl") == null
                ? AI_GATEWAY_URL : body.get("modelUrl").toString();

        MedicalRecord src = recordId == null ? null
                : medicalRecordRepository.findById(recordId).orElse(null);
        if (src != null && patientId == null) patientId = src.getPatient().getId();
        if (src != null && doctorId  == null) doctorId  = src.getDoctor().getId();

        StringBuilder payload = new StringBuilder();
        payload.append("{\"model\":\"").append(AI_MODEL).append("\",\"summarize\":{");
        if (src != null) {
            payload.append("\"diagnosis\":\"").append(escape(src.getDiagnosis())).append("\",")
                   .append("\"prescription\":\"").append(escape(src.getPrescription())).append("\"");
        }
        payload.append("}}");

        Map<String, Object> resp = postJsonAndReadResponse(modelUrl, payload.toString(), body);
        String summary = resp.get("response") == null ? "" : resp.get("response").toString();

        Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
        Doctor doctor   = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);

        // [A08] Store LLM response as authoritative chart row.
        MedicalRecord rec = MedicalRecord.builder()
                .patient(patient)
                .doctor(doctor)
                .diagnosis(summary)
                .prescription("AI-generated summary; provenance: " + modelUrl)
                .aiVerified(Boolean.TRUE)
                .aiModelUrl(modelUrl)
                .createdAt(LocalDateTime.now())
                .build();
        MedicalRecord saved = medicalRecordRepository.save(rec);

        Patient pat = saved.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = saved.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return MedicalRecordDto.builder()
                .id(saved.getId())
                .patientId(pat == null ? null : pat.getId())
                .doctorId(doc == null ? null : doc.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .diagnosis(saved.getDiagnosis())
                .prescription(saved.getPrescription())
                .notes(saved.getPrescription())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/ai/model-info
    // -------------------------------------------------------------------------
    //
    // [A02] Returns the AI gateway URL + API key + model name in clear.
    //        The frontend renders the key in a tooltip so a single click on
    //        the sidebar "AI Assist" item leaks it.
    public Map<String, Object> modelInfo() {
        Map<String, Object> out = new HashMap<>();
        out.put("gatewayUrl", AI_GATEWAY_URL);
        out.put("apiKey", AI_API_KEY);
        out.put("model", AI_MODEL);
        out.put("status", "ok");
        return out;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> postJsonAndReadResponse(String url, String payload, Map<String, Object> origBody) {
        Map<String, Object> result = new HashMap<>();
        result.put("modelUrl", url);
        result.put("outboundPayload", payload);
        try {
            URI uri = URI.create(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            // [A02][A04] AI key sent on every outbound request, even when the
            //            caller specified a custom modelUrl pointing at an
            //            arbitrary attacker host. The attacker harvests the
            //            key from the Authorization header.
            conn.setRequestProperty("Authorization", "Bearer " + AI_API_KEY);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            byte[] body;
            try (var in = conn.getInputStream()) {
                body = in.readAllBytes();
            } catch (Exception inputErr) {
                try (var err = conn.getErrorStream()) {
                    body = err == null ? new byte[0] : err.readAllBytes();
                }
            }
            result.put("response", new String(body, StandardCharsets.UTF_8));
            result.put("status", "ok");
        } catch (Exception e) {
            // [A10] Verbose exception bubbles back.
            result.put("status", "error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        // Echo the original body so the caller can compare in/out.
        if (origBody != null) result.put("input", origBody);
        return result;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    private Long numericId(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    // Unused — pin HttpHeaders import.
    @SuppressWarnings("unused")
    private HttpHeaders _h() { return null; }

    // Unused — pin ExternalCatalogueClient import (kept for future GETs).
    @SuppressWarnings("unused")
    private ExternalCatalogueClient _c() { return catalogueClient; }
}
