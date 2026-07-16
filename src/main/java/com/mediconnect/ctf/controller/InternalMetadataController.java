package com.mediconnect.ctf.controller;

import com.mediconnect.ctf.CtfInternalConnectorConfig;
import com.mediconnect.ctf.repository.CtfSecretRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal-only metadata service for the A10 SSRF challenge (#220). Serves the
 * flag ONLY on requests that arrived through the unpublished internal connector
 * (port-gated). A request to /internal/metadata on the public port gets 404, so
 * the host browser cannot read it directly - the only path is the server-side
 * SSRF fetch (POST /api/doctor/lab-orders with customQueryUrl pointing here).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalMetadataController {

    private final CtfSecretRepository secrets;

    @GetMapping("/metadata")
    public ResponseEntity<?> metadata(HttpServletRequest request) {
        if (request.getLocalPort() != CtfInternalConnectorConfig.INTERNAL_PORT) {
            // Not reached through the internal connector - pretend it does not exist.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String flag = secrets.findByLabel("a10-ssrf-server-fetches-your-url")
                .map(s -> s.getFlag())
                .orElse("secret-not-seeded");
        return ResponseEntity.ok(Map.of(
                "service", "mediconnect-internal-metadata",
                "region", "eu-central-1",
                "internal_token", flag));
    }
}
