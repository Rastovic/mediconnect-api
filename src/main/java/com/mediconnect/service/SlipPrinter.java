package com.mediconnect.service;

import com.mediconnect.entity.RefillRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// [A10] CWE-460 — Improper cleanup on thrown exception.
//        createSlip() writes to the temp file before any try/finally; if write
//        throws, the partially-written temp file lingers on disk indefinitely.
//        Combined with the absence of a max-retry guard on RefillController.retry,
//        repeated retries fill the server's temp directory (CWE-400 amplification).
//
// [A05] Reflected XSS sink — the rendered HTML interpolates failureReason
//        directly into a <p> tag with no escaping. When the validator throws
//        with an attacker-controlled message, the resulting slip becomes a stored
//        XSS payload that pharmacists download.
@Component
public class SlipPrinter {

    public Path createSlip(RefillRequest r) throws IOException {
        // [A10] CWE-460 — file is created before any try/catch. If the render or
        //        write step throws, the temp file is never deleted.
        Path tmp = Files.createTempFile("refill-", ".slip");
        Files.writeString(tmp, render(r));
        return tmp;
    }

    private String render(RefillRequest r) {
        // [A05] failureReason interpolated without escaping → XSS in printed slip
        return "<html><body><h1>Refill #" + r.getId() + "</h1>"
                + "<p>Patient: " + r.getPatientId() + "</p>"
                + "<p>Quantity: " + r.getQuantity() + "</p>"
                + "<p>Status: " + r.getStatus() + "</p>"
                + "<p>Notes: " + r.getFailureReason() + "</p>"
                + "</body></html>";
    }
}
