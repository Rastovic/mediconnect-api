package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// [A01] Returned by GET /api/admin/users/{id} — no role check on the endpoint.
//        Bundles the existing UserDto (which already leaks passwordHash, lockedUntil,
//        failedLoginAttempts) plus the user's recent audit-log entries so the
//        UserDetailDrawer shows last-login IP and failed-login history.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailDto {
    private UserDto user;
    // [A04] Includes raw stackTrace details (LoggingInterceptor writes full traces
    //        into audit_logs.details). Returned with no filtering.
    private List<AuditLogDto> recentLogs;
    private String lastLoginIp;
    private String lastLoginUserAgent;
}
