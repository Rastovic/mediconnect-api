package com.mediconnect.ctf.service;

import com.mediconnect.ctf.entity.CtfChallenge;
import com.mediconnect.ctf.entity.CtfSetting;
import com.mediconnect.ctf.repository.CtfSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The entire gating mechanism (plan §5.2). Whether a challenge is OPEN is derived
 * at read time from ctf_settings; LOCKED/OPEN is never persisted, so changing
 * settings can never leave stale gating state on progress rows.
 */
@Service
@RequiredArgsConstructor
public class CtfGatingService {

    private final CtfSettingRepository settings;

    public static final String GATING_ENABLED = "gating_enabled";
    public static final String OPEN_CATEGORIES = "open_categories";
    public static final String FORCE_OPEN_SLUGS = "force_open_slugs";
    public static final String FORCE_LOCKED_SLUGS = "force_locked_slugs";

    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        settings.findAll().forEach(s -> out.put(s.getKey(), s.getValue()));
        return out;
    }

    private String get(String key, String dflt) {
        return settings.findById(key).map(CtfSetting::getValue).orElse(dflt);
    }

    private Set<String> csv(String key) {
        String raw = get(key, "");
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** True if the challenge is currently reachable/scorable for a student. */
    public boolean isOpen(CtfChallenge challenge) {
        if (csv(FORCE_LOCKED_SLUGS).contains(challenge.getSlug())) return false;
        if (csv(FORCE_OPEN_SLUGS).contains(challenge.getSlug())) return true;

        boolean gating = Boolean.parseBoolean(get(GATING_ENABLED, "false"));
        if (!gating) return true;

        return csv(OPEN_CATEGORIES).contains(challenge.getOwaspCategory());
    }

    /** Upsert a single setting key. */
    public void put(String key, String value) {
        CtfSetting s = settings.findById(key).orElseGet(() -> {
            CtfSetting n = new CtfSetting();
            n.setKey(key);
            return n;
        });
        s.setValue(value);
        settings.save(s);
    }
}
