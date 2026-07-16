package com.mediconnect.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/**
 * Centralised input sanitisation (defense-in-depth against stored XSS).
 * {@link #clean} keeps a small safe subset of formatting tags; {@link #text}
 * strips all markup, leaving plain text.
 */
@Service
public class SanitizerService {

    private static final Safelist SAFE = Safelist.basic().preserveRelativeLinks(true);

    public String clean(String html) {
        return html == null ? null : Jsoup.clean(html, SAFE);
    }

    public String text(String s) {
        return s == null ? null : Jsoup.clean(s, Safelist.none());
    }
}
