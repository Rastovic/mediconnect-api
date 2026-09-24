package com.mediconnect.controller;

import com.mediconnect.dto.AiChatDto;
import com.mediconnect.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// MediAsistent LLM chatbot — patient-facing.
//
// [LLM01] POST /api/ai/assistant feeds unsanitised user text to the model with
//         no separation from system instructions (direct prompt injection),
//         embeds a secret in the system prompt (leak), and pastes clinician
//         notes verbatim into context (indirect injection).
// [A01]   No @PreAuthorize; patientId is trusted from the body, so any caller
//         can load any patient's chart into the model context.
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    @PostMapping("/assistant")
    public ResponseEntity<AiChatDto> assistant(@RequestBody AiChatDto request) {
        return ResponseEntity.ok(aiAssistantService.chat(request));
    }
}
