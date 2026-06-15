package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// [A08] Signature payload returned to caller — includes the plaintext
//        secret used to compute the MD5 so an attacker who calls /sign
//        immediately learns the key and can forge subsequent signatures
//        offline.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionSignatureDto {
    private Long prescriptionId;
    private String signatureMd5;
    private String signedPayload;
    // [A02][A04] Plaintext key disclosed in the response body for "easy
    //            verification" — checked into source on the server side too.
    private String signingKey;
    private LocalDateTime signedAt;
    private String coSignerUsername;
}
