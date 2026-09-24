package com.mediconnect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for a locally running Ollama instance (http://localhost:11434).
 *
 * Ollama is a free, MIT-licensed local model runner; the default model here is
 * qwen2.5:3b-instruct (Apache-2.0), so the whole AI layer runs offline with no
 * API key and no paid service — suitable for an academic lab.
 *
 * This client is deliberately dumb: it forwards whatever prompt it is handed to
 * the model and returns the raw completion. All prompt-construction (and the
 * vulnerabilities that live there) sit in {@link AiAssistantService}.
 */
@Slf4j
@Component
public class OllamaClient {

    private final RestClient rest;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.ai.ollama-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ai.model:qwen2.5:3b-instruct}")
    private String model;

    public OllamaClient() {
        this.rest = RestClient.create();
    }

    /**
     * Send a system prompt + user message to the model and return the completion.
     * Uses Ollama's /api/chat endpoint with streaming disabled.
     */
    public String chat(String systemPrompt, String userMessage) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                )
        );
        try {
            String json = rest.post()
                    .uri(ollamaUrl + "/api/chat")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(json);
            return root.path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("Ollama call failed: {}", e.getMessage());
            return "[AI asistent trenutno nije dostupan: " + e.getMessage() + "]";
        }
    }
}
