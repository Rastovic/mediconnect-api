package com.mediconnect.interceptor;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

// [A08] CWE-400 Uncontrolled Resource Consumption (Memory Exhaustion).
//
//  ContentCachingRequestWrapper has two constructors:
//    ContentCachingRequestWrapper(HttpServletRequest request)
//    ContentCachingRequestWrapper(HttpServletRequest request, int contentCacheLimit)
//
//  The single-argument form used here buffers the ENTIRE request body into a
//  byte[] in heap memory with no upper bound. An attacker can send a single
//  multipart/form-data request carrying a multi-gigabyte payload and exhaust
//  JVM heap, triggering OutOfMemoryError and taking the service offline.
//
//  This is compounded by application.yaml:
//    spring.servlet.multipart.max-file-size: -1    (unlimited)
//    spring.servlet.multipart.max-request-size: -1 (unlimited)
//
//  Secure: use the two-argument constructor with a safe limit, e.g.:
//    new ContentCachingRequestWrapper(request, 64 * 1024)  // 64 KB cap
//  and enforce multipart limits in application.yaml.
@Component
@Order(1)
public class ContentCachingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // [A08] No configuration of any size limit during filter initialisation
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // [A08] No contentCacheLimit argument — entire body buffered without bound.
        //        Attack: send Content-Length: 2147483647 (2 GB) → JVM heap exhausted.
        //        Even without Content-Length, a chunked-transfer stream is read until EOF.
        ContentCachingRequestWrapper  cachedRequest  = new ContentCachingRequestWrapper(httpRequest);

        // [A08] Response body also buffered without limit — a large API response
        //        (e.g., GET /api/users returning 100 000 records) is held in heap twice:
        //        once by the controller and once by this wrapper before copyBodyToResponse().
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(httpResponse);

        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } finally {
            // Copy the buffered response bytes to the actual response stream.
            // If OutOfMemoryError is thrown inside chain.doFilter(), this line
            // may never execute — the client receives an abrupt connection reset.
            cachedResponse.copyBodyToResponse();
        }
    }

    @Override
    public void destroy() {}
}
