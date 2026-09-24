package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request/response payload for the MediAsistent LLM chatbot (POST /api/ai/assistant).
//
// Fixed build: the response no longer echoes the prompt, and any patientId in
// the request is ignored — the chart loaded into context is always resolved
// from the authenticated principal (see AiAssistantService).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatDto {

    // Inbound: the patient's question. Still untrusted, but now isolated from
    // the system instructions by delimiters + an explicit ignore rule.
    private String message;

    // Deprecated / ignored on input. Kept only for wire compatibility with the
    // vulnerable build's clients. Authorization is never based on this value.
    @Deprecated
    private Long patientId;

    // Outbound: the model's answer.
    private String reply;
}
