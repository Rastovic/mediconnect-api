package com.mediconnect.service;

import com.mediconnect.entity.Prescription;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

// [A08] Software or Data Integrity Failures.
//        Prescription signature = MD5( id : medication : dosage : SECRET ).
//        The secret is a hardcoded String literal — checked into source
//        and additionally returned in the API response so attacker learns
//        the key on the very first sign call.
//
// [A04] Cryptographic Failures (secondary): MD5 is collision-broken;
//        symmetric key embedded in source; no asymmetric primitive,
//        no certificate chain, no PKCS#7 wrapper on the generated PDF.
@Component
public class PrescriptionSigner {

    // [A02][A04] Hardcoded signing secret — visible in source.
    public static final String SECRET = "medi-sig-key-2024";

    public String md5Signature(Prescription rx) {
        try {
            String payload = (rx.getId() == null ? "0" : String.valueOf(rx.getId())) + ":"
                    + (rx.getMedicationName() == null ? "" : rx.getMedicationName()) + ":"
                    + (rx.getDosage() == null ? "" : rx.getDosage()) + ":"
                    + SECRET;
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(payload.getBytes());
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("Signer failed: " + e.getMessage(), e);
        }
    }

    public String signedPayload(Prescription rx) {
        return (rx.getId() == null ? "0" : String.valueOf(rx.getId())) + ":"
                + (rx.getMedicationName() == null ? "" : rx.getMedicationName()) + ":"
                + (rx.getDosage() == null ? "" : rx.getDosage());
    }
}
