package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

// Returned by GET /api/admin/dashboard.
// [A01] No role check — counts and recent events are returned to any caller.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDto {
    private Map<String, Long> userCountsByRole;
    private Map<String, Long> appointmentCountsByStatus;
    private Map<String, Long> prescriptionCountsByStatus;
    private Map<String, Long> refillCountsByStatus;
    private long failedLoginsLast24h;
    private long lockedAccounts;
    private long totalAuditLogs;
    private List<AuditLogDto> recentEvents;
}
