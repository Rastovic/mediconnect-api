package com.mediconnect.ctf;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records that a behavioral CTF challenge's illegal action actually succeeded
 * (plan §12.1). The vulnerable endpoints call mark(slug) on the success path;
 * the CTF detector (CtfBehaviorController) awards the flag when a slug is marked.
 *
 * Static + in-memory on purpose: the vulnerable services can flag an event with
 * a single call and no Spring wiring, and a full app restart / DB re-seed clears
 * it (the intended reset). This is a learning aid, not a security boundary.
 */
public final class CtfBehaviorRegistry {

    private CtfBehaviorRegistry() {}

    private static final Set<String> REACHED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, AtomicInteger> COUNTERS = new ConcurrentHashMap<>();

    /** Increment and return the count for a key. Used to detect genuine races
     *  (e.g. two concurrent callers both passing a check-then-act guard). */
    public static int bump(String key) {
        return COUNTERS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    /** Called from a vulnerable endpoint when its illegal action succeeds. */
    public static void mark(String slug) {
        if (slug != null) REACHED.add(slug);
    }

    public static boolean reached(String slug) {
        return REACHED.contains(slug);
    }

    /** Per-challenge reset (replay). */
    public static void clear(String slug) {
        REACHED.remove(slug);
    }

    /** Full reset of all behavioral marks + race counters (game re-seed, §12.4). */
    public static void clearAll() {
        REACHED.clear();
        COUNTERS.clear();
    }
}
