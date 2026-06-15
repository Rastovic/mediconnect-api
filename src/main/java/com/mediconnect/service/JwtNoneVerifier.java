package com.mediconnect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// [A08] Software or Data Integrity Failures — the verifier accepts JWTs
//        signed with `alg: none`. It also accepts HS256-signed JWTs without
//        verifying the HMAC (signature bytes ignored). The decoded `sub`
//        claim is returned as the signer identity.
//
// [A07] Authentication Failures (secondary): identity asserted by the caller
//        is trusted verbatim.
@Component
public class JwtNoneVerifier {

    private final ObjectMapper mapper = new ObjectMapper();

    public String extractSubjectUnsafe(String jwt) {
        if (jwt == null || jwt.isBlank()) return null;
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            byte[] payload = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode node = mapper.readTree(new String(payload, StandardCharsets.UTF_8));
            // [A08] No signature verification, no algorithm check, no expiry check.
            JsonNode sub = node.get("sub");
            return sub == null ? null : sub.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String padBase64(String s) {
        switch (s.length() % 4) {
            case 2:  return s + "==";
            case 3:  return s + "=";
            default: return s;
        }
    }
}
