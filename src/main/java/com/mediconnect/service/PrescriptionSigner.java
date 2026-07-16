package com.mediconnect.service;

import com.mediconnect.entity.Prescription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

// Prescription integrity signature = HMAC-SHA256( id : medication : dosage )
// keyed with a secret loaded from the environment. The key is never returned
// to callers and never checked into source.
@Component
public class PrescriptionSigner {

    private final byte[] key;

    public PrescriptionSigner(@Value("${app.prescription.sign.key}") String signKey) {
        this.key = signKey.getBytes(StandardCharsets.UTF_8);
    }

    public String signature(Prescription rx) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal(signedPayload(rx).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new RuntimeException("Signer failed", e);
        }
    }

    public String signedPayload(Prescription rx) {
        return (rx.getId() == null ? "0" : String.valueOf(rx.getId())) + ":"
                + (rx.getMedicationName() == null ? "" : rx.getMedicationName()) + ":"
                + (rx.getDosage() == null ? "" : rx.getDosage());
    }
}
