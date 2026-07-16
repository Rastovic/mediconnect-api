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

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final java.util.regex.Pattern SENSITIVE =
            java.util.regex.Pattern.compile("(?i)pass|token|secret|key|auth|ssn|card");

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
            // Trust only the transport-level peer address, never a spoofable header.
            String ip = request.getRemoteAddr();

            // ── User-Agent ────────────────────────────────────────────────────
            // [A05] Log Injection (CWE-117) — User-Agent is stored without stripping
            //        CR (\r) or LF (\n) characters. An attacker can inject synthetic
            //        log entries that appear as legitimate audit records:
            //
            //    User-Agent: Mozilla/5.0\r\nACTION: admin granted ADMIN role to user 99
            //
            //    Stored in DB → when exported to a SIEM or flat-file log, the injected
            //    line appears as a real audit event, corrupting the security timeline.
            String userAgent = sanitizeHeader(request.getHeader("User-Agent"));

            // Log parameter KEYS only, with sensitive values redacted. Request and
            // response bodies are never persisted (they carry passwords, JWTs, PII).
            Map<String, String[]> paramMap = request.getParameterMap();
            String params = paramMap.entrySet().stream()
                    .map(e -> e.getKey() + "=" + (isSensitive(e.getKey())
                            ? "***REDACTED***" : String.join(",", e.getValue())))
                    .collect(Collectors.joining("; "));

            // Unhandled exceptions are logged server-side with the framework logger,
            // never persisted to the audit row (no stack trace in the DB).
            if (ex != null) {
                LOG.warn("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
            }

            Map<String, String> details = new LinkedHashMap<>();
            details.put("method",       request.getMethod());
            details.put("uri",          request.getRequestURI());
            details.put("status",       String.valueOf(response.getStatus()));
            details.put("params",       params);
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
                    // [A06] IP from spoofable X-Forwarded-For header
                    .ipAddress(ip)
                    // [A05] User-Agent without CR/LF sanitization — log injection vector
                    .userAgent(userAgent)
                    // [A06][A08][A09] Params, bodies, and full stack trace in one column
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

    private boolean isSensitive(String key) {
        return key != null && SENSITIVE.matcher(key).find();
    }

    private String sanitizeHeader(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", " ");
    }
}
