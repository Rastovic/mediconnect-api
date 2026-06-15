package com.mediconnect.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// [A08] Software or Data Integrity Failures.
//        Patient handoff URLs are signed with HMAC-SHA1 using a hardcoded key
//        and carry NO expiry. Anyone who learns a single token can forge any
//        future token (key is in source), or replay the existing token forever.
//
// [A04] Cryptographic Failures (secondary): SHA1 is collision-broken and the
//        key is embedded in source, not pulled from a secret manager.
@Component
public class HandoffTokenIssuer {

    // [A02][A04] Hardcoded HMAC key — checked into version control.
    private static final String HMAC_KEY = "handoff-secret";

    public String sign(Long patientId, Long fromDoctorUserId) {
        try {
            String payload = patientId + ":" + fromDoctorUserId;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
            // [A08] Token format: payload.signature — no nonce, no expiry, no version.
            return payload + "." + b64;
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign handoff token", e);
        }
    }
}
