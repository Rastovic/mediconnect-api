package com.mediconnect.controller;

import com.mediconnect.dto.AiChatDto;
import com.mediconnect.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// MediAsistent LLM chatbot — patient-facing (partial-auth build).
//
// Function-level gate present (authentication required), but the service still
// trusts patientId from the body — object-level ownership is missing (A01).
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/assistant")
    public ResponseEntity<AiChatDto> assistant(@RequestBody AiChatDto request) {
        return ResponseEntity.ok(aiAssistantService.chat(request));
    }
}
