package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request/response payload for the MediAsistent LLM chatbot (POST /api/ai/assistant).
//
// partial-auth build: a function-level gate is present (authentication required)
// and a keyword blacklist tries to stop prompt injection, but the object-level
// ownership check is missing — patientId is still trusted from the body.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatDto {

    private String message;

    // [A01] Still trusted from the body — no ownership check (object-level gate
    //        removed). Any authenticated user can load any patient's chart.
    private Long patientId;

    private String reply;
}
