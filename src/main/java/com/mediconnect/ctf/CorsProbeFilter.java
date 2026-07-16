package com.mediconnect.ctf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

/**
 * [A02 #28] Blanket security-config weakening detector.
 *
 * SecurityConfig disables CSRF, drops all security response headers, and applies
 * a wildcard CORS policy (allowedOrigins "*"). A state-changing request that
 * arrives from a genuinely foreign origin therefore succeeds where a hardened
 * config would have blocked it. This filter records that a cross-origin,
 * state-changing request was accepted - proof the blanket weakening is live.
 */
@Component
@Order(1)
public class CorsProbeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String origin = request.getHeader("Origin");
            String method = request.getMethod();
            boolean stateChanging = "POST".equalsIgnoreCase(method)
                    || "PUT".equalsIgnoreCase(method)
                    || "DELETE".equalsIgnoreCase(method)
                    || "PATCH".equalsIgnoreCase(method);
            if (origin != null && !origin.isBlank() && stateChanging) {
                String originHost = new URI(origin).getHost();
                String serverHost = request.getServerName();
                // Foreign host (ignoring port) → genuinely cross-origin.
                if (originHost != null && !originHost.equalsIgnoreCase(serverHost)) {
                    CtfBehaviorRegistry.mark("a02-blanket-securityconfig-weakening");
                }
            }
        } catch (Exception ignored) {
            // detection only — never break the request
        }
        chain.doFilter(request, response);
    }
}
