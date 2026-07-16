package com.mediconnect.service;

import com.mediconnect.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Co-sign identity verifier. Delegates to JwtUtil, which verifies the HMAC
// signature and rejects alg:none and algorithm-confusion tokens. Returns the
// authenticated subject only for a validly signed, unexpired token.
@Component
@RequiredArgsConstructor
public class JwtNoneVerifier {

    private final JwtUtil jwtUtil;

    /** @return the verified `sub`, or null if the JWT is missing/invalid/expired. */
    public String extractSubjectUnsafe(String jwt) {
        if (jwt == null || jwt.isBlank()) return null;
        try {
            return jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            return null;
        }
    }
}
