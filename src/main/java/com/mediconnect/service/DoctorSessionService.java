package com.mediconnect.service;

import com.mediconnect.dto.TelemedicineSessionDto;
import com.mediconnect.entity.*;
import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorSessionService {

    private final TelemedicineSessionRepository sessionRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final ExternalCatalogueClient catalogueClient;

    private static final Path RECORDINGS_DIR = Path.of("recordings");
    private static final SecureRandom RND = new SecureRandom();
    private static final String TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public List<TelemedicineSessionDto> list() {
        return sessionRepository.findAllByOrderByScheduledAtDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TelemedicineSessionDto get(Long id) {
        return toDto(sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found: " + id)));
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/sessions
    // -------------------------------------------------------------------------
    //
    // [A02] Token issued as a short random string and embedded in the room URL
    //        query string. Leaks via Referer, browser history, server access
    //        logs, screenshots.
    public TelemedicineSessionDto create(Map<String, Object> body) {
        Long patientId = numericId(body, "patientId");
        Long doctorId  = numericId(body, "doctorId");
        String reason  = body.get("reasonForVisit") == null ? "" : body.get("reasonForVisit").toString();
        String scheduledAt = body.get("scheduledAt") == null ? null : body.get("scheduledAt").toString();

        String token = generateToken();
        Patient patient = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
        Doctor doctor   = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);

        TelemedicineSession session = TelemedicineSession.builder()
                .patient(patient)
                .doctor(doctor)
                .roomUrl("/doctor/telemedicine/room?session=" + UUID.randomUUID() + "&token=" + token)
                .joinToken(token)
                .reasonForVisit(reason)
                .status("OPEN")
                .scheduledAt(parseDateTime(scheduledAt))
                .createdAt(LocalDateTime.now())
                .build();
        return toDto(sessionRepository.save(session));
    }

    public TelemedicineSessionDto end(Long id) {
        TelemedicineSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found: " + id));
        s.setStatus("ENDED");
        s.setEndedAt(LocalDateTime.now());
        return toDto(sessionRepository.save(s));
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/sessions/{id}/recording
    // -------------------------------------------------------------------------
    //
    // [A03] recordingUrl is caller-supplied. Server fetches it via URLConnection
    //        (file://, http://, https://, ftp: all reachable).
    // [A02] basename of URL written verbatim as filename — path traversal.
    public TelemedicineSessionDto attachRecording(Long id, Map<String, Object> body) {
        TelemedicineSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found: " + id));
        String url = body == null || body.get("recordingUrl") == null
                ? null : body.get("recordingUrl").toString();
        if (url == null || url.isBlank()) {
            throw new RuntimeException("recordingUrl is required");
        }
        try {
            Files.createDirectories(RECORDINGS_DIR);
            HttpHeaders[] hdr = new HttpHeaders[1];
            byte[] bytes = catalogueClient.fetchBytes(url, hdr);
            // [A02] basename — query params, traversal segments slip through.
            String basename = url.replaceFirst(".*/", "");
            if (basename.isBlank()) basename = "recording-" + System.currentTimeMillis();
            Path path = RECORDINGS_DIR.resolve(basename);
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            s.setRecordingUrl(url);
            s.setRecordingPath(path.toString());
            return toDto(sessionRepository.save(s));
        } catch (Exception e) {
            throw new RuntimeException("Attach failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/sessions/calendar.ics
    // -------------------------------------------------------------------------
    //
    // [A02] No auth required. Filtered only by ?doctorId — caller-controlled.
    //        SUMMARY field carries reason_for_visit verbatim, including HIV
    //        status / mental health / pregnancy / drug-use reasons.
    // [A01] Doctor enumeration: incrementing `?doctorId=` reveals every
    //        doctor's calendar without authentication.
    public String icalFeed(Long doctorId) {
        List<TelemedicineSession> rows = doctorId == null
                ? sessionRepository.findAllByOrderByScheduledAtDesc()
                : sessionRepository.findByDoctorIdOrderByScheduledAtDesc(doctorId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//mediconnect//doctor-telemedicine//EN\r\n");
        for (TelemedicineSession s : rows) {
            String dt = s.getScheduledAt() == null ? "" : s.getScheduledAt().format(fmt);
            String reason = s.getReasonForVisit() == null ? "Consultation" : s.getReasonForVisit();
            String patientName = s.getPatient() == null || s.getPatient().getUser() == null
                    ? "patient" : (s.getPatient().getUser().getFirstName() + " "
                                 + s.getPatient().getUser().getLastName()).trim();
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:session-").append(s.getId()).append("@mediconnect\r\n");
            if (!dt.isEmpty()) {
                sb.append("DTSTART:").append(dt).append("\r\n");
            }
            // [A02] PHI in SUMMARY and DESCRIPTION.
            sb.append("SUMMARY:").append(patientName).append(" — ").append(reason).append("\r\n");
            sb.append("DESCRIPTION:").append("Join token: ").append(s.getJoinToken())
              .append(" · ").append(s.getRoomUrl()).append("\r\n");
            sb.append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generateToken() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(TOKEN_ALPHABET.charAt(RND.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }

    private Long numericId(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }

    private TelemedicineSessionDto toDto(TelemedicineSession s) {
        Patient pat = s.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = s.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return TelemedicineSessionDto.builder()
                .id(s.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorId(doc == null ? null : doc.getId())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .roomUrl(s.getRoomUrl())
                .joinToken(s.getJoinToken())
                .reasonForVisit(s.getReasonForVisit())
                .status(s.getStatus())
                .scheduledAt(s.getScheduledAt())
                .startedAt(s.getStartedAt())
                .endedAt(s.getEndedAt())
                .recordingUrl(s.getRecordingUrl())
                .recordingPath(s.getRecordingPath())
                .note(s.getNote())
                .createdAt(s.getCreatedAt())
                .build();
    }

    // unused — placeholder so StandardCharsets import isn't pruned
    @SuppressWarnings("unused")
    private String _u() { return new String(new byte[0], StandardCharsets.UTF_8); }
}
