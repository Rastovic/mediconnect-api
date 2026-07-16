package com.mediconnect.ctf.service;

import com.mediconnect.ctf.entity.CtfChallenge;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Flag verification. Per-row salted sha256 (plan §5.1) - deliberately NOT the
 * weak hash students crack in the A04 challenges, so the CTF layer itself is
 * not a broken artifact. Not a security boundary: honor-based, local.
 */
@Service
public class CtfFlagService {

    /** sha256(salt || flag). */
    public String hash(String salt, String flag) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((salt + flag).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time compare of the hashed submission against the stored hash. */
    public boolean verify(CtfChallenge challenge, String submittedFlag) {
        if (submittedFlag == null) return false;
        String candidate = hash(challenge.getFlagSalt(), submittedFlag.trim());
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                challenge.getFlagHash().getBytes(StandardCharsets.UTF_8));
    }

    /** Hash of a raw submission for the audit log (never store the plaintext). */
    public String hashForLog(String salt, String submittedFlag) {
        return submittedFlag == null ? null : hash(salt, submittedFlag.trim());
    }
}
