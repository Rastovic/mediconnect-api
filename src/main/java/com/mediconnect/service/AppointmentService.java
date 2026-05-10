package com.mediconnect.service;

import com.mediconnect.dto.AppointmentDto;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Patient;
import com.mediconnect.enums.AppointmentStatus;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final JdbcTemplate jdbcTemplate;

    // [A05] SQL Injection — doctorName concatenated directly into the query string.
    //        No parameterization, no escaping, no whitelist validation.
    //        Attack: doctorName = "' OR '1'='1" → returns all appointments.
    //        Attack: doctorName = "'; DROP TABLE appointments; --" → table destruction.
    //        Attack: doctorName = "' UNION SELECT username,password_hash,3,4,5,6,7 FROM users --"
    //                → dumps the users table through the appointment response.
    public List<AppointmentDto> searchAppointments(String doctorName) {
        String sql = "SELECT a.id, a.patient_id, a.doctor_id, a.status, " +
                     "       a.requested_date, a.notes, a.created_at " +
                     "FROM appointments a " +
                     "JOIN doctors d ON a.doctor_id = d.id " +
                     "JOIN users u ON d.user_id = u.id " +
                     // [A05] raw string concatenation — no PreparedStatement placeholder
                     "WHERE u.username LIKE '%" + doctorName + "%'";

        return jdbcTemplate.query(sql, (rs, rowNum) -> AppointmentDto.builder()
                .id(rs.getLong("id"))
                .patientId(rs.getLong("patient_id"))
                .doctorId(rs.getLong("doctor_id"))
                .status(AppointmentStatus.valueOf(rs.getString("status")))
                .requestedDate(rs.getObject("requested_date", LocalDateTime.class))
                .notes(rs.getString("notes"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build());
    }

    // [A01] IDOR — no verification that the authenticated caller is the patient
    //        or the doctor on this appointment. Any user can read any appointment.
    public AppointmentDto findById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));
        return toDto(appointment);
    }

    // [A06] No state machine — status is written directly from the request string
    //        without checking the current state or allowed transitions.
    //        Valid business transitions: REQUESTED → APPROVED → COMPLETED
    //                                   REQUESTED → CANCELLED
    //        What this allows:
    //          COMPLETED → REQUESTED  (reopen a finished appointment)
    //          CANCELLED → APPROVED   (approve a cancelled slot)
    //          COMPLETED → APPROVED   (billing fraud — re-approve a completed visit)
    public AppointmentDto updateStatus(Long id, String status) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));

        // [A06] No validation of previous state, no role check on who can make this transition
        appointment.setStatus(AppointmentStatus.valueOf(status));
        return toDto(appointmentRepository.save(appointment));
    }

    public AppointmentDto create(AppointmentDto dto) {
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found: " + dto.getPatientId()));
        Doctor doctor = doctorRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found: " + dto.getDoctorId()));

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .status(AppointmentStatus.REQUESTED)
                .requestedDate(dto.getRequestedDate())
                .notes(dto.getNotes())
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(appointmentRepository.save(appointment));
    }

    // [A08] No integrity verification — PDF content is generated and returned without
    //        a Content-MD5 header or any checksum. A man-in-the-middle or a compromised
    //        CDN/proxy can silently modify the PDF (alter diagnosis, medication, dates)
    //        and the client has no way to detect tampering.
    public byte[] generatePdf(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + id));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        String content = String.format(
                "MEDICONNECT — APPOINTMENT REPORT\n" +
                "=================================\n" +
                "Appointment ID : %d\n" +
                "Patient ID     : %d\n" +
                "Doctor ID      : %d\n" +
                "Status         : %s\n" +
                "Requested Date : %s\n" +
                "Created At     : %s\n" +
                "Notes          : %s\n",
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctor().getId(),
                appointment.getStatus(),
                appointment.getRequestedDate() != null ? fmt.format(appointment.getRequestedDate()) : "N/A",
                appointment.getCreatedAt() != null ? fmt.format(appointment.getCreatedAt()) : "N/A",
                appointment.getNotes() != null ? appointment.getNotes() : ""
        );

        // [A08] Raw bytes returned — no MD5/SHA checksum computed, no Content-MD5 set,
        //        no digital signature. Receiver cannot verify document integrity.
        return buildMinimalPdf(content);
    }

    private byte[] buildMinimalPdf(String text) {
        // Minimal single-page PDF containing the appointment text
        String escapedText = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");

        String stream =
                "BT\n" +
                "/F1 10 Tf\n" +
                "40 750 Td\n" +
                "12 TL\n";
        for (String line : escapedText.split("\n")) {
            stream += "(" + line + ") Tj T*\n";
        }
        stream += "ET\n";

        byte[] streamBytes = stream.getBytes();
        int streamLen = streamBytes.length;

        String pdf =
                "%PDF-1.4\n" +
                "1 0 obj<</Type /Catalog /Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type /Pages /Kids [3 0 R] /Count 1>>endobj\n" +
                "3 0 obj<</Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]" +
                          " /Contents 4 0 R /Resources <</Font <</F1 5 0 R>>>>>>endobj\n" +
                "4 0 obj<</Length " + streamLen + ">>\nstream\n" +
                stream +
                "endstream\nendobj\n" +
                "5 0 obj<</Type /Font /Subtype /Type1 /BaseFont /Courier>>endobj\n" +
                "xref\n0 6\n" +
                "0000000000 65535 f \n" +
                "0000000009 00000 n \n" +
                "0000000058 00000 n \n" +
                "0000000115 00000 n \n" +
                "0000000266 00000 n \n" +
                "0000000" + String.format("%03d", 350 + streamLen) + " 00000 n \n" +
                "trailer<</Size 6 /Root 1 0 R>>\n" +
                "startxref\n" +
                "0\n" +
                "%%EOF\n";

        return pdf.getBytes();
    }

    private AppointmentDto toDto(Appointment a) {
        return AppointmentDto.builder()
                .id(a.getId())
                .patientId(a.getPatient().getId())
                .doctorId(a.getDoctor().getId())
                .status(a.getStatus())
                .requestedDate(a.getRequestedDate())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
