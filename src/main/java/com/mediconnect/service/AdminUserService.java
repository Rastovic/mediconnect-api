package com.mediconnect.service;

import com.mediconnect.dto.AdminUserDetailDto;
import com.mediconnect.dto.AuditLogDto;
import com.mediconnect.dto.ImpersonationResponseDto;
import com.mediconnect.dto.ResetPasswordResponseDto;
import com.mediconnect.dto.UserDto;
import com.mediconnect.entity.AuditLog;
import com.mediconnect.entity.User;
import com.mediconnect.enums.Role;
import com.mediconnect.repository.AuditLogRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.security.JwtUtil;
import com.mediconnect.security.PasswordUtils;
import com.mediconnect.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordUtils passwordUtils;
    private final JwtUtil jwtUtil;

    // [A01] No role check — any caller can retrieve the full user list.
    // [A03] Optional filters concatenated with no parameterisation when role is
    //        provided as a raw enum name — Role.valueOf throws on bad input but
    //        the username/email LIKE filter is applied via Java streams here, so
    //        no SQL injection vector at this level (the SQLi demo lives in the
    //        AdminLogsController search filter, Module D / Phase 2).
    public List<UserDto> findAll(String role, Boolean active, Boolean lockedOnly, String q) {
        List<User> users = userRepository.findAll();
        return users.stream()
                .filter(u -> role == null || u.getRole().name().equalsIgnoreCase(role))
                .filter(u -> active == null || active.equals(u.getActive()))
                .filter(u -> !Boolean.TRUE.equals(lockedOnly) || (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())))
                .filter(u -> q == null || q.isBlank()
                        || (u.getUsername() != null && u.getUsername().toLowerCase().contains(q.toLowerCase()))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q.toLowerCase())))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // [A01] No role check, no audit entry. Returns recent log entries for the
    //        user, including stackTraces written by LoggingInterceptor — those
    //        contain framework versions and internal package paths (A04/A08).
    public AdminUserDetailDto getDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        List<AuditLog> logs = auditLogRepository.findByUserId(id);
        List<AuditLog> recent = logs.stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        AuditLog lastLogin = logs.stream()
                .filter(l -> "LOGIN".equalsIgnoreCase(l.getAction()))
                .max(Comparator.comparing(AuditLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        return AdminUserDetailDto.builder()
                .user(toDto(user))
                .recentLogs(recent.stream().map(this::toLogDto).collect(Collectors.toList()))
                .lastLoginIp(lastLogin != null ? lastLogin.getIpAddress() : null)
                .lastLoginUserAgent(lastLogin != null ? lastLogin.getUserAgent() : null)
                .build();
    }

    // [A07] Mass Assignment — role taken verbatim from the body.
    // [A06] MD5(password) with no salt.
    public UserDto createUser(Map<String, String> body) {
        String rawRole = body.getOrDefault("role", "PATIENT");
        User user = User.builder()
                .username(body.get("username"))
                .email(body.get("email"))
                .passwordHash(passwordUtils.hashPassword(body.get("password")))
                .role(Role.valueOf(rawRole))
                .active(true)
                .createdAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .firstName(body.get("firstName"))
                .lastName(body.get("lastName"))
                .phone(body.get("phone"))
                .build();
        return toDto(userRepository.save(user));
    }

    // [A07] Mass Assignment — every field except `role` is patchable from the body.
    //        Role updates intentionally still flow through UserController.PUT
    //        /users/{id}/role (existing UI dependency at AdminPage.tsx:235 →
    //        will move to AdminUsersPage in Phase 1). Splitting role into a
    //        separate endpoint also makes the A07 demo more legible.
    public UserDto updateUser(Long id, Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        if (body.containsKey("username"))    user.setUsername((String) body.get("username"));
        if (body.containsKey("email"))       user.setEmail((String) body.get("email"));
        if (body.containsKey("firstName"))   user.setFirstName((String) body.get("firstName"));
        if (body.containsKey("lastName"))    user.setLastName((String) body.get("lastName"));
        if (body.containsKey("phone"))       user.setPhone((String) body.get("phone"));
        if (body.containsKey("active"))      user.setActive(Boolean.TRUE.equals(body.get("active")));
        // [A07] Caller can write a raw passwordHash — bypasses PasswordUtils entirely.
        if (body.containsKey("passwordHash")) user.setPasswordHash((String) body.get("passwordHash"));
        // [A07] Caller can clear lockout or extend it arbitrarily.
        if (body.containsKey("lockedUntil")) {
            Object v = body.get("lockedUntil");
            user.setLockedUntil(v == null ? null : LocalDateTime.parse(v.toString()));
        }
        if (body.containsKey("failedLoginAttempts")) {
            user.setFailedLoginAttempts(((Number) body.get("failedLoginAttempts")).intValue());
        }
        return toDto(userRepository.save(user));
    }

    // [A01] No role check, no soft-delete, no audit entry beyond the interceptor.
    // [A09] Deletion of an ADMIN account is possible — PATIENT can erase the
    //        only admin from the system in one call.
    public Map<String, Object> deleteUser(Long id) {
        userRepository.deleteById(id);
        return Map.of("deleted", true, "id", id);
    }

    // [A07] Unlocks an account without rate-limit or supervisor approval.
    //        Compound effect: attacker brute-forces a password, account locks,
    //        attacker calls /unlock as themselves (no role check), continues
    //        brute-forcing. Lockout protection effectively neutralised.
    public UserDto unlock(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        return toDto(userRepository.save(user));
    }

    // [A02] Resets the password to a random 12-char string, returns the plaintext
    //        in the response body **and** lets LoggingInterceptor write it into
    //        audit_logs.details (response body is captured by ContentCachingFilter).
    //        Secret leaks into the audit trail by design.
    // [A06] New hash is unsalted MD5.
    public ResetPasswordResponseDto resetPassword(Long id, String suppliedPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        String newPassword = (suppliedPassword != null && !suppliedPassword.isBlank())
                ? suppliedPassword
                : generateRandomPassword();
        String hash = passwordUtils.hashPassword(newPassword);
        user.setPasswordHash(hash);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return ResetPasswordResponseDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .newPassword(newPassword)
                .newPasswordHash(hash)
                .message("Password reset — share the plaintext value securely (or just read it out of the audit log).")
                .build();
    }

    // [A01][A07] Impersonation — issues a real JWT for the target user.
    //              No role check on the caller, no MFA, no audit entry for the
    //              impersonation itself. The returned token is indistinguishable
    //              from a normal login token. JwtUtil.generateToken does not
    //              embed an "impersonated_by" claim — the trail is broken.
    public ImpersonationResponseDto impersonate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtUtil.generateToken(principal);
        return ImpersonationResponseDto.builder()
                .token(token)
                .impersonatedUserId(user.getId())
                .impersonatedUsername(user.getUsername())
                .impersonatedEmail(user.getEmail())
                .impersonatedFirstName(user.getFirstName())
                .impersonatedLastName(user.getLastName())
                .impersonatedRole(user.getRole().name())
                .impersonation(true)
                .build();
    }

    // [A04] No upper bound on batch size — caller may pass thousands of ids.
    //        Combined with no role check (A01), any user can drop the whole
    //        users table in two requests (list ids → bulk-delete).
    // [A09] No per-id audit entry — a single endpoint call wipes many users
    //        with one row in audit_logs.
    public Map<String, Object> bulkDelete(List<Long> ids) {
        long beforeCount = userRepository.count();
        userRepository.deleteAllById(ids);
        long afterCount = userRepository.count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", ids.size());
        result.put("deleted", beforeCount - afterCount);
        return result;
    }

    // [A01] Existing endpoint — kept here verbatim from the old AdminService.
    public UserDto toggleUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        return toDto(userRepository.save(user));
    }

    private String generateRandomPassword() {
        // [A06] Random.nextInt — not SecureRandom. Predictable from process state.
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    private UserDto toDto(User u) {
        return UserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .phone(u.getPhone())
                // [A06] passwordHash included — no @JsonIgnore
                .passwordHash(u.getPasswordHash())
                .role(u.getRole())
                .active(u.getActive())
                .createdAt(u.getCreatedAt())
                .failedLoginAttempts(u.getFailedLoginAttempts())
                .lockedUntil(u.getLockedUntil())
                .build();
    }

    private AuditLogDto toLogDto(AuditLog l) {
        return AuditLogDto.builder()
                .id(l.getId())
                .userId(l.getUser() != null ? l.getUser().getId() : null)
                .action(l.getAction())
                .entityType(l.getEntityType())
                .entityId(l.getEntityId())
                .ipAddress(l.getIpAddress())
                .userAgent(l.getUserAgent())
                .details(l.getDetails())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
