package com.mediconnect.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    // Signing secret injected from the environment — never committed. Must be a
    // base64 string decoding to at least 32 bytes (256 bits). No fallback: the
    // app fails to start without it.
    @Value("${app.jwt.secret}")
    private String secret;

    // 30-minute access token.
    private static final long EXPIRATION_MS = 30L * 60 * 1000;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret must be valid base64", e);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(decoded);
    }

    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        // verifyWith(SecretKey) locks the parser to HMAC with this key:
        // alg:none tokens (unsecured JWS) and RS256 confusion are both rejected.
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
