package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A10] CWE-754 — quantity is intentionally an Integer (boxed) and nullable.
//        The frontend "Request Refill" button submits quantity=null on purpose so
//        the eligibility validator dereferences a null Integer and throws NPE,
//        which the fail-open catch in RefillQueueService.processOne then swallows.
//
// [A07] requestedBy is taken from the request body — caller can attribute the
//        refill request to any user id without authentication.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefillCreateDto {
    private Long prescriptionId;
    private Long patientId;
    // [A07] body-supplied actor — never re-checked against the JWT
    private Long requestedBy;
    // [A10] nullable on purpose
    private Integer quantity;
}
