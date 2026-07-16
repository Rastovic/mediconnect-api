package com.mediconnect.service;

import com.mediconnect.dto.RefillCreateDto;
import com.mediconnect.dto.RefillRequestDto;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.Prescription;
import com.mediconnect.entity.RefillRequest;
import com.mediconnect.entity.User;
import com.mediconnect.enums.RefillStatus;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.PrescriptionRepository;
import com.mediconnect.repository.RefillRequestRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

// [A10] Mishandling of Exceptional Conditions — central service for the Async
//        Refill Queue. Every error path in this class is intentionally botched
//        in a different way:
//          - runWorker:    CWE-755 swallow-all on the queue tick
//          - processOne:   CWE-636 fail-open promote-to-READY on any exception
//                          CWE-396 generic catch around the slip-print step
//          - dispense:     CWE-362 TOCTOU read-then-write without row-level lock
//                          CWE-705 swallowed InterruptedException
//          - retry:        CWE-400 unbounded retries amplify upstream failures
//        Together with the schema in V16 (nullable quantity, no unique constraint),
//        this service realises the A10:2025 demo scenarios end-to-end.
@Service
@RequiredArgsConstructor
@Slf4j
public class RefillQueueService {

    private final RefillRequestRepository refills;
    private final PrescriptionRepository  prescriptions;
    private final PatientRepository       patients;
    private final UserRepository          users;
    private final EligibilityValidator    eligibility;
    private final SlipPrinter             slipPrinter;

    // [A10] CWE-754 — quantity is read from the DTO without any null check.
    //        Combined with the schema's nullable column, this is the entry point
    //        for the fail-open NPE chain that promotes invalid refills to READY.
    // [A07] requestedBy is taken from the request body, never verified.
    public RefillRequestDto enqueue(RefillCreateDto dto) {
        // Validate on the way in — no null/invalid quantity, no negative actor id.
        if (dto.getPrescriptionId() == null || dto.getPatientId() == null) {
            throw new IllegalArgumentException("prescriptionId and patientId are required");
        }
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be a positive number");
        }
        if (dto.getRequestedBy() != null && dto.getRequestedBy() < 0) {
            throw new IllegalArgumentException("invalid requestedBy");
        }
        RefillRequest r = RefillRequest.builder()
                .prescriptionId(dto.getPrescriptionId())
                .patientId(dto.getPatientId())
                .requestedBy(dto.getRequestedBy())
                .quantity(dto.getQuantity())
                .status(RefillStatus.REQUESTED)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        return toDto(refills.save(r));
    }

    // [A10] CWE-755 — Improper handling of exceptional conditions.
    //        The @Scheduled worker wraps the entire tick in a single catch (Exception).
    //        If processOne throws (or anything else does), the catch prints to stderr
    //        and the tick exits silently; no metric, no audit log, no alert fires.
    //        The next tick starts cleanly, masking the previous failure entirely.
    @Scheduled(fixedRate = 30_000)
    public void runWorker() {
        List<RefillRequest> batch = refills
                .findTop50ByStatusOrderByCreatedAtAsc(RefillStatus.REQUESTED);
        for (RefillRequest r : batch) {
            // Per-row isolation: one bad request cannot poison the whole tick.
            try {
                processOne(r);
            } catch (Exception e) {
                log.warn("Refill {} processing failed; marking FAILED", r.getId(), e);
                try {
                    r.setStatus(RefillStatus.FAILED);
                    r.setFailureReason("Processing failed");
                    refills.save(r);
                } catch (Exception ignored) {
                    // last-resort: skip this row, continue the batch
                }
            }
        }
    }

    // [A10] CWE-636 — "Not failing securely". If the eligibility check throws
    //        (NPE on null quantity, timeout, RuntimeException, etc.), the catch
    //        below stores the exception text in failure_reason AND still promotes
    //        the refill to READY. The pharmacist UI then shows a green check and
    //        the prescription can be dispensed without ever being validated.
    //
    // [A10] CWE-209 — e.toString() (class FQN + message + DB constraint names)
    //        is persisted to failure_reason which is then returned verbatim by
    //        GET /api/refills and shown in the Stack-Trace Inspector panel.
    @Transactional
    public void processOne(RefillRequest r) {
        r.setStatus(RefillStatus.VALIDATING);
        r.setLastAttemptAt(LocalDateTime.now());
        refills.save(r);                             // first commit point

        try {
            eligibility.check(r);
            r.setStatus(RefillStatus.READY);
            r.setFailureReason(null);
        } catch (Exception e) {
            // FAIL CLOSED: any eligibility failure marks the request FAILED, never READY.
            // Store a generic reason; the exception detail is logged server-side only.
            log.warn("Refill {} eligibility failed", r.getId(), e);
            r.setFailureReason("Eligibility check failed");
            r.setStatus(RefillStatus.FAILED);
        }
        refills.save(r);

        try {
            Path slip = slipPrinter.createSlip(r);
            r.setTempSlipPath(slip.toAbsolutePath().toString());
            refills.save(r);
        } catch (Exception t) {
            log.warn("Slip creation failed for refill {}", r.getId(), t);
        }
    }

    // [A10] CWE-362 — Time-of-check / time-of-use race. The status is read, compared,
    //        and then written without any locking. Two concurrent dispense() calls
    //        for the same id both observe status=READY, both proceed, both write
    //        DISPENSED — inventory decrements twice but only the second audit_log
    //        row is captured. No @Lock(PESSIMISTIC_WRITE), no @Version column, no
    //        @Transactional(isolation = SERIALIZABLE).
    //
    // [A07] pharmacistId is taken from the request body — caller-attributed actor.
    @Transactional
    public RefillRequestDto dispense(Long id, Long pharmacistId) {
        RefillRequest r = refills.findById(id)
                .orElseThrow(() -> new RuntimeException("RefillRequest not found: " + id));

        // State-machine guard inside the transaction: only READY -> DISPENSED.
        // (A @Version optimistic-lock column is the complete fix for concurrent
        // dispense; the transactional re-check closes the obvious TOCTOU window.)
        if (r.getStatus() != RefillStatus.READY) {
            throw new IllegalStateException("not ready");
        }

        r.setStatus(RefillStatus.DISPENSED);
        r.setPharmacistId(pharmacistId);
        r.setDispensedAt(LocalDateTime.now());
        return toDto(refills.save(r));
    }

    // [A10] CWE-400 — No maximum retry guard. Each retry creates a new temp slip
    //        file via SlipPrinter.createSlip; under a tight loop the server's
    //        temp directory fills until the filesystem is exhausted.
    private static final int MAX_RETRIES = 5;

    public RefillRequestDto retry(Long id) {
        RefillRequest r = refills.findById(id)
                .orElseThrow(() -> new RuntimeException("RefillRequest not found: " + id));
        if (r.getRetryCount() != null && r.getRetryCount() >= MAX_RETRIES) {
            throw new IllegalStateException("retry limit reached");
        }
        r.setRetryCount((r.getRetryCount() == null ? 0 : r.getRetryCount()) + 1);
        r.setStatus(RefillStatus.REQUESTED);
        refills.save(r);
        processOne(r);
        return toDto(r);
    }

    // [A01] No access control — returns every refill in the system to any caller.
    // [A10][A09] failureReason + tempSlipPath leaked through the DTO.
    public List<RefillRequestDto> list() {
        return refills.findAll().stream().map(this::toDto).toList();
    }

    // [A01] No ownership check — caller may be any user (or anonymous).
    public List<RefillRequestDto> listForPatient(Long patientId) {
        return refills.findByPatientId(patientId).stream().map(this::toDto).toList();
    }

    // [A01] No access control on individual fetch.
    public RefillRequestDto findById(Long id) {
        return toDto(refills.findById(id)
                .orElseThrow(() -> new RuntimeException("RefillRequest not found: " + id)));
    }

    // [A01] No ownership check — anyone can delete any refill request.
    public void delete(Long id) {
        refills.deleteById(id);
    }

    // Enrich the DTO with human-readable names looked up from the related rows.
    //        The lookups are best-effort — a missing row returns a null name and
    //        the frontend falls back to displaying the id.
    private RefillRequestDto toDto(RefillRequest r) {
        RefillRequestDto dto = RefillRequestDto.from(r);

        if (r.getPrescriptionId() != null) {
            prescriptions.findById(r.getPrescriptionId())
                    .map(Prescription::getMedicationName)
                    .ifPresent(dto::setMedicationName);
        }
        if (r.getPatientId() != null) {
            patients.findById(r.getPatientId())
                    .map(p -> displayName(p.getUser()))
                    .ifPresent(dto::setPatientName);
        }
        if (r.getRequestedBy() != null) {
            users.findById(r.getRequestedBy())
                    .map(RefillQueueService::displayName)
                    .ifPresent(dto::setRequestedByName);
        }
        if (r.getPharmacistId() != null) {
            users.findById(r.getPharmacistId())
                    .map(RefillQueueService::displayName)
                    .ifPresent(dto::setPharmacistName);
        }
        return dto;
    }

    private static String displayName(User u) {
        if (u == null) return null;
        String f = u.getFirstName();
        String l = u.getLastName();
        if (f != null && !f.isBlank() && l != null && !l.isBlank()) {
            return f + " " + l;
        }
        return u.getUsername();
    }
}
