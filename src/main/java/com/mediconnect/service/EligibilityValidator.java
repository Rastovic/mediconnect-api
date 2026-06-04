package com.mediconnect.service;

import com.mediconnect.entity.RefillRequest;
import org.springframework.stereotype.Component;

// [A10] CWE-754 — Improper check for unusual or exceptional conditions.
//        The validator dereferences `r.getQuantity()` (a boxed Integer) without
//        any null check. When `quantity` is NULL (which the schema permits and
//        the frontend "Request Refill" button submits on purpose), the auto-unboxing
//        comparison throws NullPointerException.
//
//        Downstream, RefillQueueService.processOne catches that NPE generically
//        and promotes the row to READY anyway → CWE-636 fail-open authorisation.
@Component
public class EligibilityValidator {

    public void check(RefillRequest r) {
        // [A10] CWE-754 — no null guard. r.getQuantity() may be null.
        if (r.getQuantity() > 0 && r.getQuantity() <= 90) {
            return;
        }
        throw new IllegalArgumentException(
                "quantity out of range — refill_requests row " + r.getId()
                        + " (constraint chk_quantity_range)");
    }
}
