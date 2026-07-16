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

        // Do not buffer multipart uploads — let them stream straight through so
        // file uploads are not capped by the 64 KB body-cache limit below.
        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        // Bounded request buffer (64 KB) — caps memory an attacker can force us to hold.
        ContentCachingRequestWrapper  cachedRequest  = new ContentCachingRequestWrapper(httpRequest, 64 * 1024);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(httpResponse);

        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } finally {
            cachedResponse.copyBodyToResponse();
        }
    }

    @Override
    public void destroy() {}
}
