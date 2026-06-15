package com.mediconnect.service;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

// [A03] URL.openConnection sink used by the Module C / D / E / F SSRF demos.
//        - No allow-list of hosts
//        - file://, http://, https://, jar:, ftp: all resolved (JDK URL
//          protocol handlers participate without filtering)
//        - HTTP redirects followed by default — attacker can redirect to
//          internal hosts (cloud metadata, RFC1918 ranges) by returning
//          302 from a public server
//        - Returns raw body to caller, so blind SSRF turns into reflected
//          SSRF
@Component
public class ExternalCatalogueClient {

    public String fetch(String url) {
        byte[] data = fetchBytes(url, null);
        return new String(data, StandardCharsets.UTF_8);
    }

    public byte[] fetchBytes(String url, HttpHeaders[] outHeaders) {
        try {
            URI uri = URI.create(url);
            URL u = uri.toURL();
            URLConnection conn = u.openConnection();
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            if (conn instanceof HttpURLConnection http) {
                http.setInstanceFollowRedirects(true);
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
            // [A10] Verbose error returned so caller can fingerprint internal
            //        services by the exception type / message.
            return ("FETCH_ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
