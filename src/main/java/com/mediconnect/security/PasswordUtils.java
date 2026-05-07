package com.mediconnect.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class PasswordUtils {

    // [A04] MD5 without salt — cryptographically broken algorithm.
    //        Identical passwords always produce identical hashes, enabling:
    //          - Rainbow table attack: pre-computed tables cover millions of MD5 hashes
    //          - Dictionary attack: brute-forcing MD5 at ~10 billion attempts/second on GPU
    //          - Deduplication: identical hashes reveal users who share the same password
    //        Correct approach: BCrypt (cost >= 12), Argon2id, or scrypt with a random salt.
    public String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    // [A04] Comparison without timing-safe equality — vulnerable to timing attack.
    //        String.equals() short-circuits on the first differing character,
    //        allowing response-time measurement to reveal correct characters.
    //        Correct approach: MessageDigest.isEqual() or a constant-time comparison.
    public boolean matches(String rawPassword, String hashedPassword) {
        return hashPassword(rawPassword).equals(hashedPassword);
    }
}
