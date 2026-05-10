package com.mediconnect.interceptor;

import com.mediconnect.entity.AuditLog;
import com.mediconnect.entity.User;
import com.mediconnect.repository.AuditLogRepository;
import com.mediconnect.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LoggingInterceptor implements HandlerInterceptor {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute("_startTime", System.currentTimeMillis());
        return true;
    }

    // afterCompletion fires after the controller method returns (and after any
    // exception resolver runs). The Exception parameter is non-null only when an
    // unhandled exception bubbles up past the controller.
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        try {
            // ── IP address ────────────────────────────────────────────────────
            // [A04] X-Forwarded-For header taken at face value — trivially spoofable.
            //        An attacker sends "X-Forwarded-For: 127.0.0.1" and their real IP
            //        is never recorded. Stored verbatim in audit_logs.ip_address.
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.getRemoteAddr();
            }

            // ── User-Agent ────────────────────────────────────────────────────
            // [A05] Log Injection (CWE-117) — User-Agent is stored without stripping
            //        CR (\r) or LF (\n) characters. An attacker can inject synthetic
            //        log entries that appear as legitimate audit records:
            //
            //    User-Agent: Mozilla/5.0\r\nACTION: admin granted ADMIN role to user 99
            //
            //    Stored in DB → when exported to a SIEM or flat-file log, the injected
            //    line appears as a real audit event, corrupting the security timeline.
            String userAgent = request.getHeader("User-Agent"); // [A05] no CR/LF strip

            // ── Request parameters ────────────────────────────────────────────
            // [A04][A09] All query and form parameters written to the DB without masking.
            //        Affected fields include "password", "token", "creditCard", "ssn".
            //
            //    POST /api/auth/login body:  {"username":"admin","password":"secret123"}
            //    → stored: params=username=admin; password=secret123;
            //
            //    A compromised DB read gives an attacker plaintext credentials for every
            //    login attempt ever made through the application.
            //
            //    Secure: replace the value of any key matching /pass|token|secret|key|auth/i
            //            with "***REDACTED***" before persisting.
            Map<String, String[]> paramMap = request.getParameterMap();
            String params = paramMap.entrySet().stream()
                    .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                    .collect(Collectors.joining("; "));

            // ── Request body ──────────────────────────────────────────────────
            // [A04][A09] Raw JSON / form body logged verbatim.
            //        Captures {"password":"secret"} payloads sent to /api/auth/login
            //        or {"role":"ADMIN"} payloads sent to privilege-escalation endpoints.
            String requestBody = "";
            if (request instanceof ContentCachingRequestWrapper ccr) {
                byte[] buf = ccr.getContentAsByteArray();
                if (buf.length > 0) {
                    requestBody = new String(buf, StandardCharsets.UTF_8);
                }
            }

            // ── Response body ─────────────────────────────────────────────────
            // [A04] Response body stored in the audit log — may contain JWT tokens,
            //        passwordHash values, or PII returned by the API.
            //        GET /api/users/1 response: {"id":1,"passwordHash":"5f4dcc3b..."}
            //        → the hash is now duplicated in the audit_logs table.
            String responseBody = "";
            if (response instanceof ContentCachingResponseWrapper ccr) {
                byte[] buf = ccr.getContentAsByteArray();
                if (buf.length > 0) {
                    responseBody = new String(buf, StandardCharsets.UTF_8);
                }
            }

            // ── Stack trace ───────────────────────────────────────────────────
            // [A08] CWE-209 — Information Exposure Through an Error Message.
            //        ex.printStackTrace(pw) captures the full JVM stack trace including:
            //          - Internal class names and package structure
            //          - Framework versions (Spring, Hibernate, Tomcat)
            //          - SQL query text (from JPA exceptions)
            //          - File paths (from IO exceptions)
            //          - Library dependency chain
            //        All of this is persisted to the audit_logs.details column and is
            //        readable by anyone who can query GET /api/admin/logs (no role check).
            //
            //        Secure: log the exception with a correlation ID, store only the ID
            //        in the response; keep the full trace in a write-only log sink.
            String stackTrace = null;
            if (ex != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                ex.printStackTrace(pw); // [A08] full JVM stack trace persisted to DB
                stackTrace = sw.toString();
            }

            // ── Build details string ──────────────────────────────────────────
            Map<String, String> details = new LinkedHashMap<>();
            details.put("method",       request.getMethod());
            details.put("uri",          request.getRequestURI());
            details.put("status",       String.valueOf(response.getStatus()));
            details.put("params",       params);
            details.put("requestBody",  requestBody);
            details.put("responseBody", responseBody);
            if (stackTrace != null) {
                details.put("stackTrace", stackTrace); // [A08] infrastructure leakage
            }
            long duration = System.currentTimeMillis()
                    - (long) request.getAttribute("_startTime");
            details.put("durationMs", String.valueOf(duration));

            String detailsJson = details.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\": \"" + escape(e.getValue()) + "\"")
                    .collect(Collectors.joining(", ", "{", "}"));

            // ── Resolve caller identity from JWT SecurityContext ───────────────
            User currentUser = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getName())) {
                currentUser = userRepository.findByUsername(auth.getName()).orElse(null);
            }

            AuditLog log = AuditLog.builder()
                    .user(currentUser)
                    .action(request.getMethod() + " " + request.getRequestURI())
                    .entityType("HTTP_REQUEST")
                    // [A04] IP from spoofable X-Forwarded-For header
                    .ipAddress(ip)
                    // [A05] User-Agent without CR/LF sanitization — log injection vector
                    .userAgent(userAgent)
                    // [A04][A08][A09] Params, bodies, and full stack trace in one column
                    .details(detailsJson)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(log);

        } catch (Exception ignored) {
            // Logging failure must never break the request — swallowed silently.
            // [A09] Swallowing logging errors means audit gaps go unnoticed.
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
