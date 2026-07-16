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
    private LocalDateTime signedAt;
    private String coSignerUsername;
}
