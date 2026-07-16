package com.mediconnect.service;

import com.mediconnect.dto.*;
import com.mediconnect.entity.*;
import com.mediconnect.enums.AppointmentStatus;
import com.mediconnect.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorAppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // GET /api/doctor/appointments
    // -------------------------------------------------------------------------
    //
    // [A05] `q` concatenated raw into JPQL — same pattern as Module A roster.
    // [A01] doctorId is taken verbatim — no JWT correlation, any caller
    //        enumerates any doctor's calendar.
    public List<AppointmentRowDto> list(Long doctorId, String status, String from, String to, String q) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.patient_id, a.doctor_id, a.status, a.requested_date, " +
                "       a.original_date, a.rescheduled_at, a.notes, a.decline_reason, " +
                "       a.actor_doctor_id, a.no_show, a.created_at " +
                "FROM appointments a WHERE 1=1 "
        );
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (doctorId != null) {
            sql.append(" AND a.doctor_id = ?").append(params.size() + 1);
            params.add(doctorId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?").append(params.size() + 1);
            params.add(status);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND a.requested_date >= ?").append(params.size() + 1);
            params.add(from);
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND a.requested_date <= ?").append(params.size() + 1);
            params.add(to);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND a.notes LIKE ?").append(params.size() + 1);
            params.add("%" + q + "%");
        }
        sql.append(" ORDER BY a.requested_date ASC");
        Query nq = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            nq.setParameter(i + 1, params.get(i));
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = nq.getResultList();
        return rows.stream().map(this::rowToDto).collect(Collectors.toList());
    }

    public List<AppointmentRowDto> today(Long doctorId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = LocalDate.now().atTime(LocalTime.MAX);
        return list(doctorId, null, start.toString(), end.toString(), null);
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/appointments/conflicts
    // -------------------------------------------------------------------------
    //
    // [A01] Returns ALL doctors' conflicts (with patient PII) when called
    //        without a doctorId filter — full enumeration in one call.
    // [A06] Insecure design — overlapping appointments stay APPROVED because
    //        the approve endpoint never checks the slot is free. Conflicts
    //        endpoint surfaces the bug; no remediation hook.
    public List<ConflictPairDto> conflicts(Long doctorId) {
        List<Appointment> rows = doctorId == null
                ? appointmentRepository.findAll()
                : appointmentRepository.findByDoctorId(doctorId);
        // O(n^2) — fine for demo, the data set is small.
        List<ConflictPairDto> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Appointment a = rows.get(i);
            if (a.getDoctor() == null || a.getRequestedDate() == null) continue;
            for (int j = i + 1; j < rows.size(); j++) {
                Appointment b = rows.get(j);
                if (b.getDoctor() == null || b.getRequestedDate() == null) continue;
                if (!a.getDoctor().getId().equals(b.getDoctor().getId())) continue;
                long diffMin = Math.abs(java.time.Duration.between(a.getRequestedDate(), b.getRequestedDate()).toMinutes());
                if (diffMin < 30) {
                    out.add(ConflictPairDto.builder()
                            .doctorId(a.getDoctor().getId())
                            .doctorName(fullName(a.getDoctor().getUser()))
                            .firstId(a.getId())
                            .secondId(b.getId())
                            .firstPatientName(fullName(a.getPatient() == null ? null : a.getPatient().getUser()))
                            .secondPatientName(fullName(b.getPatient() == null ? null : b.getPatient().getUser()))
                            .firstScheduledAt(a.getRequestedDate())
                            .secondScheduledAt(b.getRequestedDate())
                            .build());
                }
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Workflow actions
    // -------------------------------------------------------------------------
    //
    // [A06] /approve does NOT check the slot is free → double-book demo.
    // [A07] actorDoctorId taken from body.
    public AppointmentRowDto approve(Long id, Map<String, Object> body) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));
        a.setStatus(AppointmentStatus.APPROVED);
        a.setActorDoctorId(numericId(body, "actorDoctorId"));
        return toDto(appointmentRepository.save(a));
    }

    // [A05] declineReason stored verbatim, rendered as HTML in the list.
    public AppointmentRowDto decline(Long id, Map<String, Object> body) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setDeclineReason(body == null || body.get("declineReason") == null
                ? null : body.get("declineReason").toString());
        a.setActorDoctorId(numericId(body, "actorDoctorId"));
        return toDto(appointmentRepository.save(a));
    }

    // [A08] Overwrites requestedDate in place; only ONE prior value preserved
    //        in originalDate. No revision table, no audit row.
    public AppointmentRowDto reschedule(Long id, Map<String, Object> body) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));
        String newAt = body == null || body.get("newScheduledAt") == null
                ? null : body.get("newScheduledAt").toString();
        if (newAt == null) throw new RuntimeException("newScheduledAt is required");
        a.setOriginalDate(a.getRequestedDate());
        a.setRequestedDate(LocalDateTime.parse(newAt));
        a.setRescheduledAt(LocalDateTime.now());
        if (body.get("reason") != null) {
            // Reason appended to notes — no separator escaping.
            a.setNotes((a.getNotes() == null ? "" : a.getNotes() + " | ") + body.get("reason"));
        }
        return toDto(appointmentRepository.save(a));
    }

    // [A08] Completing an appointment auto-creates a MedicalRecord using
    //        the caller-supplied notes as `diagnosis`. No signature, no
    //        countersignature, no audit row for chart-row creation.
    public AppointmentRowDto complete(Long id, Map<String, Object> body) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));
        a.setStatus(AppointmentStatus.COMPLETED);
        String notes = body == null || body.get("notes") == null ? "" : body.get("notes").toString();
        a.setNotes(notes);
        if (a.getPatient() != null && a.getDoctor() != null) {
            MedicalRecord rec = MedicalRecord.builder()
                    .patient(a.getPatient())
                    .doctor(a.getDoctor())
                    .appointment(a)
                    .diagnosis(notes)
                    .prescription("Auto-created from appointment #" + a.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            medicalRecordRepository.save(rec);
        }
        return toDto(appointmentRepository.save(a));
    }

    // [A06] No rate limit, no per-doctor cap — any caller can flag every
    //        appointment as no-show in one loop.
    public AppointmentRowDto noShow(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setNoShow(Boolean.TRUE);
        return toDto(appointmentRepository.save(a));
    }

    // [A06][A09] Unbounded ids list, single audit row covers N mutations.
    public Map<String, Object> bulkStatus(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> ids = body == null || !(body.get("ids") instanceof List)
                ? List.of() : (List<Object>) body.get("ids");
        String status = body == null || body.get("status") == null ? "APPROVED" : body.get("status").toString();
        AppointmentStatus s;
        try { s = AppointmentStatus.valueOf(status); }
        catch (Exception e) { throw new RuntimeException("Bad status: " + status); }
        int updated = 0;
        for (Object o : ids) {
            Long id;
            try { id = o instanceof Number n ? n.longValue() : Long.parseLong(o.toString()); }
            catch (Exception e) { continue; }
            Appointment a = appointmentRepository.findById(id).orElse(null);
            if (a != null) { a.setStatus(s); appointmentRepository.save(a); updated++; }
        }
        return Map.of("updated", updated, "requested", ids.size());
    }

    // -------------------------------------------------------------------------
    // CSV import (POST /import)
    // -------------------------------------------------------------------------
    //
    // [A06] No row cap, no size cap — large file OOMs the JVM (DoS).
    // [A03] CSV values placed into `notes` verbatim, including leading `=`
    //        which becomes a spreadsheet formula on export (#export).
    public Map<String, Object> importCsv(MultipartFile file) {
        int rows = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] cols = line.split(",", -1);
                if (cols.length < 4) continue;
                Long patientId = parseLong(cols[0]);
                Long doctorId  = parseLong(cols[1]);
                LocalDateTime when = LocalDateTime.parse(cols[2]);
                String notes = cols.length > 3 ? cols[3] : "";
                Patient pat = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
                Doctor doc  = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);
                if (pat == null || doc == null) continue;
                Appointment a = Appointment.builder()
                        .patient(pat)
                        .doctor(doc)
                        .status(AppointmentStatus.REQUESTED)
                        .requestedDate(when)
                        .notes(notes)
                        .createdAt(LocalDateTime.now())
                        .build();
                appointmentRepository.save(a);
                rows++;
            }
        } catch (Exception e) {
            // [A10] Errors silenced — partial imports leave no diagnostic trail.
        }
        return Map.of("imported", rows);
    }

    // -------------------------------------------------------------------------
    // CSV export (GET /export.csv)
    // -------------------------------------------------------------------------
    //
    // [A03] Cell values written verbatim. notes / patientName cells starting
    //        with =, +, -, @ are interpreted as formulas when the CSV is
    //        opened in Excel / LibreOffice → DDE / WEBSERVICE / HYPERLINK
    //        execution against the analyst's machine.
    public String exportCsv(Long doctorId) {
        List<Appointment> rows = doctorId == null
                ? appointmentRepository.findAll()
                : appointmentRepository.findByDoctorId(doctorId);
        StringBuilder sb = new StringBuilder();
        sb.append("id,patientName,doctorName,status,scheduledAt,notes,declineReason\r\n");
        for (Appointment a : rows) {
            sb.append(a.getId()).append(',')
              .append(safe(fullName(a.getPatient() == null ? null : a.getPatient().getUser()))).append(',')
              .append(safe(fullName(a.getDoctor()  == null ? null : a.getDoctor().getUser()))).append(',')
              .append(a.getStatus()).append(',')
              .append(a.getRequestedDate()).append(',')
              // [A03] notes written verbatim. =cmd|'/c calc'!A1 → RCE on Excel.
              .append(safe(a.getNotes())).append(',')
              .append(safe(a.getDeclineReason()))
              .append("\r\n");
        }
        return sb.toString();
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

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    private String safe(String s) {
        if (s == null) return "";
        String v = s.replace("\r", " ").replace("\n", " ");
        // Neutralize CSV formula injection: cells starting with = + - @ (or tab/CR)
        // are prefixed with a single quote so spreadsheets treat them as text.
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        // Quote the field and escape embedded quotes so commas are data, not delimiters.
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private String fullName(User u) {
        if (u == null) return "";
        String f = u.getFirstName(), l = u.getLastName();
        return ((f == null ? "" : f) + " " + (l == null ? "" : l)).trim();
    }

    private AppointmentRowDto rowToDto(Object[] r) {
        Long patientId = r[1] == null ? null : ((Number) r[1]).longValue();
        Long doctorId  = r[2] == null ? null : ((Number) r[2]).longValue();
        Patient pat = patientId == null ? null : patientRepository.findById(patientId).orElse(null);
        Doctor doc  = doctorId  == null ? null : doctorRepository.findById(doctorId).orElse(null);
        return AppointmentRowDto.builder()
                .id(((Number) r[0]).longValue())
                .patientId(patientId)
                .patientName(fullName(pat == null ? null : pat.getUser()))
                .doctorId(doctorId)
                .doctorName(fullName(doc == null ? null : doc.getUser()))
                .status(r[3] == null ? null : AppointmentStatus.valueOf(r[3].toString()))
                .scheduledAt(toLdt(r[4]))
                .originalDate(toLdt(r[5]))
                .rescheduledAt(toLdt(r[6]))
                .notes((String) r[7])
                .declineReason((String) r[8])
                .actorDoctorId(r[9] == null ? null : ((Number) r[9]).longValue())
                .noShow(toBool(r[10]))
                .createdAt(toLdt(r[11]))
                .build();
    }

    private Boolean toBool(Object o) {
        if (o == null) return Boolean.FALSE;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(o.toString());
    }

    private LocalDateTime toLdt(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        return null;
    }

    private AppointmentRowDto toDto(Appointment a) {
        return AppointmentRowDto.builder()
                .id(a.getId())
                .patientId(a.getPatient() == null ? null : a.getPatient().getId())
                .patientName(fullName(a.getPatient() == null ? null : a.getPatient().getUser()))
                .doctorId(a.getDoctor() == null ? null : a.getDoctor().getId())
                .doctorName(fullName(a.getDoctor() == null ? null : a.getDoctor().getUser()))
                .status(a.getStatus())
                .scheduledAt(a.getRequestedDate())
                .originalDate(a.getOriginalDate())
                .rescheduledAt(a.getRescheduledAt())
                .notes(a.getNotes())
                .declineReason(a.getDeclineReason())
                .actorDoctorId(a.getActorDoctorId())
                .noShow(a.getNoShow())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
