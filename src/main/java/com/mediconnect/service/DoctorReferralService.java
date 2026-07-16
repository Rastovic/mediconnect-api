package com.mediconnect.service;

import com.mediconnect.dto.ReferralBundle;
import com.mediconnect.dto.ReferralDto;
import com.mediconnect.entity.*;
import com.mediconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorReferralService {

    private final ReferralRepository referralRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    // -------------------------------------------------------------------------
    // POST /api/doctor/referrals
    // -------------------------------------------------------------------------
    //
    // [A08] bundlePayload is stored verbatim — no validation, no class
    //        filtering at write time. Inbox + accept will deserialise it later.
    public ReferralDto create(Map<String, Object> body) {
        Long fromId = numericId(body, "fromDoctorId");
        Long toId   = numericId(body, "toDoctorId");
        Long pid    = numericId(body, "patientId");
        String subject = body.get("subject") == null ? "" : body.get("subject").toString();
        String payload = body.get("bundlePayload") == null ? null : body.get("bundlePayload").toString();

        Doctor from = fromId == null ? null : doctorRepository.findById(fromId).orElse(null);
        Doctor to   = toId   == null ? null : doctorRepository.findById(toId).orElse(null);
        Patient pat = pid    == null ? null : patientRepository.findById(pid).orElse(null);

        Referral r = Referral.builder()
                .fromDoctor(from)
                .toDoctor(to)
                .patient(pat)
                .subject(subject)
                .bundlePayload(payload)
                .accepted(Boolean.FALSE)
                .createdAt(LocalDateTime.now())
                .build();
        return toDto(referralRepository.save(r), /*deserialise*/ false);
    }

    // -------------------------------------------------------------------------
    // GET /api/doctor/referrals/inbox
    // -------------------------------------------------------------------------
    //
    // [A08] Every unread (and every read) referral's bundlePayload is run
    //        through ObjectInputStream.readObject() on page load. Class
    //        filtering is disabled. A malicious payload triggers Runtime.exec
    //        in ReferralBundle#readObject — RCE.
    // [A09] No audit row written for the deserialisation event; failures
    //        are silent (decode_status field on the row is the only signal).
    public List<ReferralDto> inbox() {
        return referralRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> toDto(r, /*deserialise*/ true))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // POST /api/doctor/referrals/{id}/accept
    // -------------------------------------------------------------------------
    //
    // [A08] Deserialises again (second RCE opportunity), then copies the
    //        decoded fields into a new MedicalRecord WITHOUT verifying
    //        provenance — `aiVerified` ribbons render the inherited bundle
    //        as authoritative.
    public ReferralDto accept(Long id) {
        Referral r = referralRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Referral not found: " + id));
        ReferralBundle bundle = decodeBundle(r.getBundlePayload());
        if (bundle != null && r.getToDoctor() != null && r.getPatient() != null) {
            MedicalRecord rec = MedicalRecord.builder()
                    .patient(r.getPatient())
                    .doctor(r.getToDoctor())
                    .diagnosis(bundle.diagnosis == null ? bundle.summary : bundle.diagnosis)
                    .prescription(bundle.medication == null ? bundle.summary : bundle.medication)
                    .createdAt(LocalDateTime.now())
                    .build();
            medicalRecordRepository.save(rec);
        }
        r.setAccepted(Boolean.TRUE);
        r.setAcceptedAt(LocalDateTime.now());
        referralRepository.save(r);
        return toDto(r, /*deserialise*/ true);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ReferralBundle decodeBundle(String b64) {
        if (b64 == null || b64.isBlank() || "BENIGN_PLACEHOLDER".equals(b64)) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            // [A08] No ObjectInputFilter, no class allow-list.
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object o = ois.readObject();
                if (o instanceof ReferralBundle b) return b;
            }
        } catch (Exception e) {
            // [A10] Silent — decode failures stored only as `decodeStatus`.
            // [CTF][A10 #254] Behavioral: a non-empty payload that fails to decode
            // is swallowed and returns null (no error), driving the unchecked-null
            // path downstream. Mark it.
            com.mediconnect.ctf.CtfBehaviorRegistry.mark("a10-silent-null-on-failed-decode");
        }
        return null;
    }

    private Long numericId(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private ReferralDto toDto(Referral r, boolean deserialise) {
        Doctor from = r.getFromDoctor();
        User fromU  = from == null ? null : from.getUser();
        Doctor to   = r.getToDoctor();
        User toU    = to == null ? null : to.getUser();
        Patient pat = r.getPatient();
        User patU   = pat == null ? null : pat.getUser();

        ReferralBundle bundle = deserialise ? decodeBundle(r.getBundlePayload()) : null;
        String status;
        if (r.getBundlePayload() == null || "BENIGN_PLACEHOLDER".equals(r.getBundlePayload())) {
            status = "placeholder";
        } else if (bundle != null) {
            status = "decoded";
        } else {
            status = deserialise ? "decode-failed" : "skipped";
        }

        return ReferralDto.builder()
                .id(r.getId())
                .fromDoctorId(from == null ? null : from.getId())
                .fromDoctorName(fromU == null ? null
                        : ((fromU.getFirstName() == null ? "" : fromU.getFirstName()) + " "
                        + (fromU.getLastName() == null ? "" : fromU.getLastName())).trim())
                .toDoctorId(to == null ? null : to.getId())
                .toDoctorName(toU == null ? null
                        : ((toU.getFirstName() == null ? "" : toU.getFirstName()) + " "
                        + (toU.getLastName() == null ? "" : toU.getLastName())).trim())
                .patientId(pat == null ? null : pat.getId())
                .patientName(patU == null ? null
                        : ((patU.getFirstName() == null ? "" : patU.getFirstName()) + " "
                        + (patU.getLastName() == null ? "" : patU.getLastName())).trim())
                .subject(r.getSubject())
                .previewPatientName(bundle == null ? null : bundle.patientName)
                .previewSummary(bundle == null ? null : bundle.summary)
                .previewDiagnosis(bundle == null ? null : bundle.diagnosis)
                .previewMedication(bundle == null ? null : bundle.medication)
                .bundlePayload(r.getBundlePayload())
                .decodeStatus(status)
                .accepted(r.getAccepted())
                .acceptedAt(r.getAcceptedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
