package com.mediconnect.service;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Outbound fetch used by the lab/catalogue integrations. Hardened against SSRF:
 * only http/https schemes, the resolved host must be a public address, and
 * redirects are not followed (a 302 to an internal host cannot be chased).
 */
@Component
public class ExternalCatalogueClient {

    public String fetch(String url) {
        byte[] data = fetchBytes(url, null);
        return new String(data, StandardCharsets.UTF_8);
    }

    public byte[] fetchBytes(String url, HttpHeaders[] outHeaders) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new SecurityException("scheme not allowed");
            }
            String host = uri.getHost();
            if (host == null) {
                throw new SecurityException("host required");
            }
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isInternal(addr)) {
                    throw new SecurityException("internal address blocked");
                }
            }

            URL u = uri.toURL();
            URLConnection conn = u.openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            if (conn instanceof HttpURLConnection http) {
                http.setInstanceFollowRedirects(false);
            }
            String ct = conn.getContentType();
            if (outHeaders != null && outHeaders.length > 0) {
                HttpHeaders h = new HttpHeaders();
                if (ct != null) h.add(HttpHeaders.CONTENT_TYPE, ct);
                outHeaders[0] = h;
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            // Generic error — never fingerprint internal services for the caller.
            return "FETCH_ERROR".getBytes(StandardCharsets.UTF_8);
        }
    }

    private boolean isInternal(InetAddress addr) {
        return addr.isLoopbackAddress()
                || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress();
    }
}
