package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A02] Returned by POST /api/admin/users/{id}/reset-password.
//        The new plaintext password is included in the response (so the
//        admin can read/copy it) and also written verbatim to audit_logs.details
//        via the logging interceptor — secret leaks into the audit trail.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordResponseDto {
    private Long userId;
    private String username;
    // [A02] plaintext password — returned to caller and persisted in audit log
    private String newPassword;
    // [A06] new MD5 hash also returned — useful for offline cracking demos
    private String newPasswordHash;
    private String message;
}
