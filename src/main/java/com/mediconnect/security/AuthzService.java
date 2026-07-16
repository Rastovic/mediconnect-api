package com.mediconnect.security;

import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.LabResultRepository;
import com.mediconnect.repository.MedicalRecordRepository;
import com.mediconnect.repository.MessageRepository;
import com.mediconnect.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Central authorization decisions for method-level {@code @PreAuthorize}.
 * The server is the only source of truth: every check reads the caller
 * identity from the {@link Authentication}, never from a client-supplied id.
 */
@Service("authz")
@RequiredArgsConstructor
public class AuthzService {

    private final AppointmentRepository appts;
    private final MedicalRecordRepository records;
    private final MessageRepository messages;
    private final PrescriptionRepository prescriptions;
    private final LabResultRepository labs;

    public boolean isSelf(Authentication auth, Long userId) {
        Long uid = userIdOf(auth);
        return uid != null && uid.equals(userId);
    }

    public boolean isAdmin(Authentication auth) { return hasRole(auth, "ADMIN"); }
    public boolean isDoctor(Authentication auth) { return hasRole(auth, "DOCTOR"); }

    public boolean canViewAppointment(Authentication auth, Long apptId) {
        if (isAdmin(auth)) return true;
        Long uid = userIdOf(auth);
        return appts.findById(apptId).map(a ->
                idEq(a.getPatient() != null && a.getPatient().getUser() != null ? a.getPatient().getUser().getId() : null, uid)
             || idEq(a.getDoctor() != null && a.getDoctor().getUser() != null ? a.getDoctor().getUser().getId() : null, uid)
        ).orElse(false);
    }

    /** Only the assigned doctor or an admin may change an appointment (patients cannot). */
    public boolean canEditAppointment(Authentication auth, Long apptId) {
        if (isAdmin(auth)) return true;
        Long uid = userIdOf(auth);
        return appts.findById(apptId).map(a ->
                idEq(a.getDoctor() != null && a.getDoctor().getUser() != null ? a.getDoctor().getUser().getId() : null, uid)
        ).orElse(false);
    }

    public boolean canViewMedicalRecord(Authentication auth, Long recordId) {
        if (isAdmin(auth)) return true;
        Long uid = userIdOf(auth);
        return records.findById(recordId).map(r ->
                idEq(r.getPatient() != null && r.getPatient().getUser() != null ? r.getPatient().getUser().getId() : null, uid)
             || idEq(r.getDoctor() != null && r.getDoctor().getUser() != null ? r.getDoctor().getUser().getId() : null, uid)
        ).orElse(false);
    }

    public boolean canViewPrescription(Authentication auth, Long rxId) {
        if (isAdmin(auth)) return true;
        Long uid = userIdOf(auth);
        return prescriptions.findById(rxId).map(p ->
                idEq(p.getPatient() != null && p.getPatient().getUser() != null ? p.getPatient().getUser().getId() : null, uid)
             || idEq(p.getDoctor() != null && p.getDoctor().getUser() != null ? p.getDoctor().getUser().getId() : null, uid)
        ).orElse(false);
    }

    public boolean canViewLabResult(Authentication auth, Long labId) {
        if (isAdmin(auth) || isDoctor(auth)) return true;
        Long uid = userIdOf(auth);
        return labs.findById(labId).map(l ->
                idEq(l.getPatient() != null && l.getPatient().getUser() != null ? l.getPatient().getUser().getId() : null, uid)
        ).orElse(false);
    }

    public boolean canViewMessage(Authentication auth, Long messageId) {
        if (isAdmin(auth)) return true;
        Long uid = userIdOf(auth);
        return messages.findById(messageId).map(m ->
                idEq(m.getSender() != null ? m.getSender().getId() : null, uid)
             || idEq(m.getReceiver() != null ? m.getReceiver().getId() : null, uid)
        ).orElse(false);
    }

    /** A patient may see their own chart; doctors and admins may see any. */
    public boolean canViewPatientChart(Authentication auth, Long patientUserId) {
        return isAdmin(auth) || isDoctor(auth) || isSelf(auth, patientUserId);
    }

    private Long userIdOf(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) return null;
        return p.getUser().getId();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private boolean idEq(Long a, Long b) {
        return a != null && a.equals(b);
    }
}
