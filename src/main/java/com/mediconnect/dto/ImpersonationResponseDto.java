package com.mediconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// [A01][A07] Returned by POST /api/admin/users/{id}/impersonate.
//             The token grants full session access as the target user.
//             No role check on the endpoint, no MFA, no audit entry for the
//             impersonation itself (the A09 demo — see ADMIN_VIEW_PLAN.md §9).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationResponseDto {
    private String token;
    private Long impersonatedUserId;
    private String impersonatedUsername;
    // [A06] Email + names returned so the UI can rebuild AuthUser without
    //        a follow-up GET. They land in localStorage, where any XSS reads them.
    private String impersonatedEmail;
    private String impersonatedFirstName;
    private String impersonatedLastName;
    private String impersonatedRole;
    // Marker so the UI knows to render ImpersonateBanner — but the JWT itself
    // is indistinguishable from a real login, so removing this flag client-side
    // hides the banner without breaking the session.
    private boolean impersonation;
}
