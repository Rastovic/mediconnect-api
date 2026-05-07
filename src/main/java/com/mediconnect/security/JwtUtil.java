package com.mediconnect.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Date;

@Component
public class JwtUtil {

    // [A02][A04] JWT secret hardcoded directly in source code.
    //            Visible to anyone with repository access — can be used to sign
    //            arbitrary tokens with any username or role.
    private static final String SECRET = "mediconnect-super-secret-2024";

    // [A07] Token valid for 30 days — an attacker who steals a token has 720 hours of access.
    //        Recommended lifetime for session tokens: 15–60 minutes.
    private static final long EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000;

    // [A02] Key shorter than 256 bits (SECRET is 30 chars = 240 bits).
    //        JJWT's WeakKeyException bypassed by zero-padding instead of using
    //        a cryptographically strong key from Keys.secretKeyFor(HS256).
    private Key getSigningKey() {
        byte[] keyBytes = Arrays.copyOf(SECRET.getBytes(StandardCharsets.UTF_8), 32);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                // [A07] Algorithm not explicitly specified — JJWT infers it from the key type.
                //        Attacker may attempt Algorithm Confusion by replacing the 'alg' header.
                .signWith(getSigningKey())
                .compact();
    }

    // [A07] Algorithm Confusion: parser does not validate or restrict the 'alg' header
    //        from incoming tokens. No call to requireAlgorithm() or allowedAlgorithms().
    //        An attacker controlling the header may attempt:
    //          - alg: "none"  → unsigned token accepted
    //          - alg: "RS256" → confusion if RSA keys are present on the classpath
    //        JJWT 0.12.3 CVE-2024-31033 further enlarges the attack surface.
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        // [A07] Validation does not check: token revocation, token type (access vs refresh),
        //        or the 'alg' claim — accepts any signed token with a matching username.
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
