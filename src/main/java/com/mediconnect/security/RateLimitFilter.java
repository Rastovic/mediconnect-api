package com.mediconnect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for the auth endpoints: 5 requests per 15 minutes
 * per (client IP + URI). Keeps brute-force / credential-stuffing in check
 * without an external dependency.
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 15L * 60 * 1000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (!uri.startsWith("/api/auth/login") && !uri.startsWith("/api/auth/register")) {
            chain.doFilter(req, res);
            return;
        }

        String key = req.getRemoteAddr() + ":" + uri;
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= WINDOW_MS) {
                return new Window(now);
            }
            return existing;
        });

        if (w.count.incrementAndGet() > MAX_REQUESTS) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long start) { this.start = start; }
    }
}
