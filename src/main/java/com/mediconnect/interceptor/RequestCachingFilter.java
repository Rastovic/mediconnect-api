package com.mediconnect.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

// Wraps every request/response so LoggingInterceptor can read body bytes
// after Spring MVC has already consumed them.
// [A06][A09] Enabling body capture is itself a vulnerability surface — the entire
//             request body (including passwords in JSON payloads) is buffered in
//             memory and then persisted to the audit_logs table verbatim.
public class RequestCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        ContentCachingRequestWrapper  wrappedRequest  = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            // Must copy buffered body bytes back to the real response stream
            wrappedResponse.copyBodyToResponse();
        }
    }
}
