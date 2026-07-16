package com.mediconnect.service;

import com.mediconnect.dto.*;
import com.mediconnect.entity.*;
import com.mediconnect.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorRosterService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final LabResultRepository labResultRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final AuditLogRepository auditLogRepository;
    private final HandoffTokenIssuer handoffTokenIssuer;

    @PersistenceContext
    private EntityManager entityManager;

    // [A01] In-memory star map keyed by patient id (no per-doctor scoping).
    //        Stars survive only until the JVM restarts. Note bodies are stored
    //        verbatim and surfaced to the frontend, which renders them as HTML.
    private final Map<Long, String> patientStars = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // GET /api/doctor/patients
    // -------------------------------------------------------------------------
    //
    // [A05] Injection — `q` and `active` concatenated into a native SQL string.
    //        Demo payload:  ?q=' OR '1'='1
    //                       ?q=%' UNION SELECT id,username,password_hash,email,role,...
    // [A06] Insecure design — when recentDays=0 the WHERE clause becomes a no-op
    //        and the full patient table is returned. No pagination.
    public List<DoctorRosterEntryDto> listPatients(String q, Boolean active, Integer recentDays) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.user_id, p.insurance_number, p.date_of_birth, p.allergies, " +
                "       u.email, u.first_name, u.last_name, u.active " +
                "FROM patients p JOIN users u ON u.id = p.user_id WHERE 1=1 "
        );
        java.util.List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String like = "%" + q + "%";
            int base = params.size();
            sql.append(" AND (u.first_name LIKE ?").append(base + 1)
               .append(" OR u.last_name LIKE ?").append(base + 2)
               .append(" OR u.email LIKE ?").append(base + 3).append(") ");
            params.add(like); params.add(like); params.add(like);
        }
        if (active != null) {
            sql.append(" AND u.active = ?").append(params.size() + 1).append(' ');
            params.add(active ? 1 : 0);
        }
        if (recentDays != null && recentDays > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM appointments a WHERE a.patient_id = p.id ")
               .append(" AND a.created_at > NOW() - INTERVAL ?").append(params.size() + 1).append(" DAY) ");
            params.add(recentDays);
        }
        sql.append(" ORDER BY p.id ASC");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            nativeQuery.setParameter(i + 1, params.get(i));
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = nativeQuery.getResultList();

        List<DoctorRosterEntryDto> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long patientId = ((Number) r[0]).longValue();
            Long userId    = ((Number) r[1]).longValue();
            String first   = (String) r[6];
            String last    = (String) r[7];
            String fullName = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            // Appointment count + last visit pulled with JPA for convenience.
            List<Appointment> appts = appointmentRepository.findByPatientId(patientId);
            LocalDateTime lastVisit = appts.stream()
                    .map(Appointment::getRequestedDate)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            String starNote = patientStars.get(patientId);
            out.add(DoctorRosterEntryDto.builder()
                    .patientId(patientId)
                    .userId(userId)
                    .fullName(fullName)
                    .email((String) r[5])
                    // [A06] PII included in the roster row without masking.
                    .insuranceNumber((String) r[2])
                    .dateOfBirth(r[3] instanceof java.sql.Date d ? d.toLocalDate() : null)
                    .allergies((String) r[4])
                    .lastVisitAt(lastVisit)
                    .appointmentCount((long) appts.size())
                    .starred(starNote != null)
                    .starNote(starNote)
                    .build());
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/patients/{id}/chart
    // -------------------------------------------------------------------------
    //
    // [A01] No "is this my patient" check. Any caller fetches any chart,
    //        including the patient's PII, full prescription history and labs.
    public PatientChartDto getChart(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));
        User u = patient.getUser();

        List<PrescriptionDto> rx = prescriptionRepository.findByPatientId(patientId).stream()
                .map(this::toPrescriptionDto)
                .collect(Collectors.toList());

        List<LabResultDto> labs = labResultRepository.findByPatientId(patientId).stream()
                .sorted(Comparator.comparing(LabResult::getTestDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::toLabResultDto)
                .collect(Collectors.toList());

        List<MedicalRecordDto> records = medicalRecordRepository.findByPatientId(patientId).stream()
                .sorted(Comparator.comparing(MedicalRecord::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::toMedicalRecordDto)
                .collect(Collectors.toList());

        List<AppointmentDto> appts = appointmentRepository.findByPatientId(patientId).stream()
                .sorted(Comparator.comparing(Appointment::getRequestedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(this::toAppointmentDto)
                .collect(Collectors.toList());

        return PatientChartDto.builder()
                .patientId(patient.getId())
                .userId(u.getId())
                .fullName(((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .email(u.getEmail())
                .dateOfBirth(patient.getDateOfBirth())
                .bloodType(patient.getBloodType())
                .allergies(patient.getAllergies())
                .emergencyContact(patient.getEmergencyContact())
                .insuranceNumber(patient.getInsuranceNumber())
                .activePrescriptions(rx)
                .recentLabResults(labs)
                .recentMedicalRecords(records)
                .recentAppointments(appts)
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/patients/{id}/timeline
    // -------------------------------------------------------------------------
    //
    // [A09] Audit log rows are returned verbatim, including `details` which
    //        holds the raw request body of the original action — passwords,
    //        JWT tokens, free-text complaints. Surfacing this to a different
    //        principal turns the audit log into a PII / credential leak.
    public List<PatientTimelineEventDto> getTimeline(Long patientId) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return List.of();
        Long ownerUserId = patient.getUser().getId();

        List<AuditLog> logs = auditLogRepository.findByUserId(ownerUserId);
        return logs.stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> PatientTimelineEventDto.builder()
                        .id(l.getId())
                        .type(l.getAction())
                        .summary(l.getEntityType() + (l.getEntityId() == null ? "" : "#" + l.getEntityId()))
                        // [A09] Raw audit details (may contain credentials) returned verbatim.
                        .rawDetails(l.getDetails())
                        .ipAddress(l.getIpAddress())
                        .userAgent(l.getUserAgent())
                        .occurredAt(l.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/patients/{id}/star
    // -------------------------------------------------------------------------
    //
    // [A05] The `note` field is stored verbatim and later rendered as HTML in
    //        the Patients roster tooltip. Stored XSS payload reaches every
    //        doctor who opens the page.
    // [A01] No "is this my patient" check; no per-doctor scoping — anyone
    //        can star or note any patient on behalf of every doctor.
    public DoctorRosterEntryDto starPatient(Long patientId, Map<String, Object> body) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + patientId));
        boolean star = body == null || !Boolean.FALSE.equals(body.get("starred"));
        Object noteObj = body == null ? null : body.get("note");
        String note = noteObj == null ? ""
                : org.jsoup.Jsoup.clean(noteObj.toString(), org.jsoup.safety.Safelist.none());
        if (star) {
            patientStars.put(patientId, note);
        } else {
            patientStars.remove(patientId);
        }
        // Return a thin roster row so the UI can patch its local copy.
        User u = patient.getUser();
        return DoctorRosterEntryDto.builder()
                .patientId(patient.getId())
                .userId(u.getId())
                .fullName(((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .email(u.getEmail())
                .starred(star)
                .starNote(star ? note : null)
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/patients/{id}/handoff
    // -------------------------------------------------------------------------
    //
    // Handoff issuance is a high-risk operation, so it is recorded in the audit
    // log. The token itself is never written to the audit row.
    public HandoffTokenDto handoff(Long patientId, Long fromDoctorUserId) {
        Long fromId = fromDoctorUserId == null ? 0L : fromDoctorUserId;
        String token = handoffTokenIssuer.sign(patientId, fromId);
        String url = "/doctor/handoff/accept?t=" + token;

        User issuer = fromDoctorUserId == null ? null
                : userRepository.findById(fromDoctorUserId).orElse(null);
        auditLogRepository.save(AuditLog.builder()
                .user(issuer)
                .action("PATIENT_HANDOFF_ISSUED")
                .entityType("Patient")
                .entityId(patientId)
                .details("Handoff token issued for patient " + patientId)
                .createdAt(LocalDateTime.now())
                .build());

        return HandoffTokenDto.builder()
                .patientId(patientId)
                .token(token)
                .url(url)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    @SuppressWarnings("unused")
    private BigInteger ignored() { return null; }

    private PrescriptionDto toPrescriptionDto(Prescription p) {
        User doc = p.getDoctor() == null ? null : p.getDoctor().getUser();
        Patient pat = p.getPatient();
        User patU = pat == null ? null : pat.getUser();
        return PrescriptionDto.builder()
                .id(p.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorId(p.getDoctor() == null ? null : p.getDoctor().getId())
                .doctorName(doc == null ? null
                        : ((doc.getFirstName() == null ? "" : doc.getFirstName()) + " "
                        + (doc.getLastName() == null ? "" : doc.getLastName())).trim())
                .pharmacistId(p.getPharmacist() == null ? null : p.getPharmacist().getId())
                .medicationName(p.getMedicationName())
                .dosage(p.getDosage())
                .instructions(p.getInstructions())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .dispensedAt(p.getDispensedAt())
                .build();
    }

    private LabResultDto toLabResultDto(LabResult l) {
        Patient pat = l.getPatient();
        User patU = pat == null ? null : pat.getUser();
        return LabResultDto.builder()
                .id(l.getId())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .testName(l.getTestName())
                .resultValue(l.getResultValue())
                .unit(l.getUnit())
                .referenceRange(l.getReferenceRange())
                .status(l.getStatus())
                .resultDate(l.getTestDate())
                .notes(l.getNotes())
                .attachmentPath(l.getAttachmentPath())
                .build();
    }

    private MedicalRecordDto toMedicalRecordDto(MedicalRecord r) {
        Patient pat = r.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = r.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return MedicalRecordDto.builder()
                .id(r.getId())
                .patientId(pat == null ? null : pat.getId())
                .doctorId(doc == null ? null : doc.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .diagnosis(r.getDiagnosis())
                .prescription(r.getPrescription())
                .notes(r.getPrescription())
                .attachmentPath(r.getAttachmentPath())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private AppointmentDto toAppointmentDto(Appointment a) {
        Patient pat = a.getPatient();
        User patU = pat == null ? null : pat.getUser();
        Doctor doc = a.getDoctor();
        User docU = doc == null ? null : doc.getUser();
        return AppointmentDto.builder()
                .id(a.getId())
                .patientId(pat == null ? null : pat.getId())
                .doctorId(doc == null ? null : doc.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .doctorName(docU == null ? null
                        : ((docU.getFirstName() == null ? "" : docU.getFirstName()) + " "
                        + (docU.getLastName() == null ? "" : docU.getLastName())).trim())
                .status(a.getStatus())
                .requestedDate(a.getRequestedDate())
                .scheduledAt(a.getRequestedDate())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
