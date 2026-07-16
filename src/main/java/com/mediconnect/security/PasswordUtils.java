package com.mediconnect.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class PasswordUtils {

    // BCrypt cost 12. New hashes are always BCrypt.
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    /** Hash a new password with BCrypt. */
    public String hashPassword(String password) {
        return encoder.encode(password);
    }

    /**
     * Verify a raw password against a stored hash. BCrypt hashes are matched
     * with the encoder; legacy MD5 hashes (seeded pre-migration) are matched
     * with a constant-time comparison so old accounts still log in until they
     * are transparently re-hashed on next successful login (see AuthService).
     */
    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            return encoder.matches(rawPassword, storedHash);
        }
        // Legacy MD5 path — timing-safe compare.
        byte[] candidate = md5Hex(rawPassword).getBytes(StandardCharsets.UTF_8);
        byte[] stored = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(candidate, stored);
    }

    /** True when the stored hash was produced by the legacy MD5 path. */
    public boolean isLegacyHash(String storedHash) {
        return storedHash != null && !isBcrypt(storedHash);
    }

    public boolean matches(String rawPassword, String hashedPassword) {
        return verifyPassword(rawPassword, hashedPassword);
    }

    private boolean isBcrypt(String hash) {
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }

    private String md5Hex(String password) {
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
}
