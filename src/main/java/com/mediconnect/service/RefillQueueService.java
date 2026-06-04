package com.mediconnect.service;

import com.mediconnect.dto.RefillCreateDto;
import com.mediconnect.dto.RefillRequestDto;
import com.mediconnect.entity.RefillRequest;
import com.mediconnect.enums.RefillStatus;
import com.mediconnect.repository.RefillRequestRepository;
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
    private final EligibilityValidator    eligibility;
    private final SlipPrinter             slipPrinter;

    // [A10] CWE-754 — quantity is read from the DTO without any null check.
    //        Combined with the schema's nullable column, this is the entry point
    //        for the fail-open NPE chain that promotes invalid refills to READY.
    // [A07] requestedBy is taken from the request body, never verified.
    public RefillRequestDto enqueue(RefillCreateDto dto) {
        RefillRequest r = RefillRequest.builder()
                .prescriptionId(dto.getPrescriptionId())
                .patientId(dto.getPatientId())
                .requestedBy(dto.getRequestedBy())
                .quantity(dto.getQuantity())        // [A10] may be null
                .status(RefillStatus.REQUESTED)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        return RefillRequestDto.from(refills.save(r));
    }

    // [A10] CWE-755 — Improper handling of exceptional conditions.
    //        The @Scheduled worker wraps the entire tick in a single catch (Exception).
    //        If processOne throws (or anything else does), the catch prints to stderr
    //        and the tick exits silently; no metric, no audit log, no alert fires.
    //        The next tick starts cleanly, masking the previous failure entirely.
    @Scheduled(fixedRate = 30_000)
    public void runWorker() {
        try {
            List<RefillRequest> batch = refills
                    .findTop50ByStatusOrderByCreatedAtAsc(RefillStatus.REQUESTED);
            for (RefillRequest r : batch) {
                processOne(r);
            }
        } catch (Exception e) {
            // [A10] CWE-755 — generic catch, swallowed exception, no rethrow.
            //        Original stack trace is printed to System.err only.
            e.printStackTrace();
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
            eligibility.check(r);                    // may throw (NPE on null quantity)
            r.setStatus(RefillStatus.READY);
            r.setFailureReason(null);
        } catch (Exception e) {
            // [A10] FAIL-OPEN — any exception is treated as success.
            //        This is the worst-case CWE-636 / CWE-755 pattern.
            r.setFailureReason(e.toString());        // [A09] raw exception text stored
            r.setStatus(RefillStatus.READY);         // [A10] fail open
        }
        refills.save(r);

        // [A10] CWE-460 — slip is created here; cleanup is only attempted inside
        //        SlipPrinter on the happy path. If createSlip throws after writing
        //        the temp file, the file lingers forever (CWE-400 amplification).
        try {
            Path slip = slipPrinter.createSlip(r);
            r.setTempSlipPath(slip.toAbsolutePath().toString());   // [A09] FS path leak
            refills.save(r);
        } catch (Throwable t) {
            // [A10] CWE-396 — catches Throwable, hiding OutOfMemoryError, ThreadDeath,
            //        StackOverflowError. The empty body is the only "log" of failure.
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
    public RefillRequestDto dispense(Long id, Long pharmacistId) {
        RefillRequest r = refills.findById(id)
                .orElseThrow(() -> new RuntimeException("RefillRequest not found: " + id));

        if (r.getStatus() != RefillStatus.READY) {
            throw new IllegalStateException("not ready");
        }

        // simulated inventory I/O — intentionally widens the race window
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
            // [A10] CWE-705 — InterruptedException swallowed; thread loses interrupt status.
            //        During graceful shutdown, the JVM interrupts worker threads; this
            //        swallow means the dispense completes anyway, producing transactions
            //        that begin after the shutdown signal.
        }

        r.setStatus(RefillStatus.DISPENSED);
        r.setPharmacistId(pharmacistId);             // [A07] body-supplied actor
        r.setDispensedAt(LocalDateTime.now());
        return RefillRequestDto.from(refills.save(r));
    }

    // [A10] CWE-400 — No maximum retry guard. Each retry creates a new temp slip
    //        file via SlipPrinter.createSlip; under a tight loop the server's
    //        temp directory fills until the filesystem is exhausted.
    public RefillRequestDto retry(Long id) {
        RefillRequest r = refills.findById(id)
                .orElseThrow(() -> new RuntimeException("RefillRequest not found: " + id));
        r.setRetryCount(r.getRetryCount() + 1);       // no upper bound
        r.setStatus(RefillStatus.REQUESTED);
        refills.save(r);
        processOne(r);                                // fail-open chain re-runs
        return RefillRequestDto.from(r);
    }

    // [A01] No access control — returns every refill in the system to any caller.
    // [A10][A09] failureReason + tempSlipPath leaked through the DTO.
    public List<RefillRequestDto> list() {
        return refills.findAll().stream().map(RefillRequestDto::from).toList();
    }

    // [A01] No ownership check — caller may be any user (or anonymous).
    public List<RefillRequestDto> listForPatient(Long patientId) {
        return refills.findByPatientId(patientId).stream().map(RefillRequestDto::from).toList();
    }

    // [A01] No access control on individual fetch.
    public RefillRequestDto findById(Long id) {
        return RefillRequestDto.from(refills.findById(id)
                .orElseThrow(() -> new RuntimeException("RefillRequest not found: " + id)));
    }

    // [A01] No ownership check — anyone can delete any refill request.
    public void delete(Long id) {
        refills.deleteById(id);
    }
}
