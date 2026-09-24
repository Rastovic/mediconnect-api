package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request/response payload for the MediAsistent LLM chatbot (POST /api/ai/assistant).
//
// [A05 / OWASP LLM01] The `message` field is free-form user text that is
//        concatenated straight into the model prompt with no separation
//        between trusted system instructions and untrusted user input —
//        the architectural precondition for Prompt Injection.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatDto {

    // Inbound: the patient's question. Attacker-controlled.
    private String message;

    // [A01] Inbound context selector, trusted from the body with no ownership
    //        check — the caller can ask the assistant to load ANY patient's
    //        chart into the prompt just by changing this id.
    private Long patientId;

    // Outbound: the model's answer.
    private String reply;

    // Outbound (debug): the full prompt that was sent to the model. Left on in
    //        the vulnerable build so the system prompt — including its secrets —
    //        leaks directly in the API response.
    private String debugPrompt;
}
