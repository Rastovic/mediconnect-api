package com.mediconnect.controller;

import com.mediconnect.dto.AiChatDto;
import com.mediconnect.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// MediAsistent LLM chatbot — patient-facing (fixed build).
//
// Authentication is required; the chart loaded into the model context is
// resolved from the principal inside the service, not from the request body.
// Rate limiting is handled by RateLimitFilter and the call is audited by
// LoggingInterceptor.
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
