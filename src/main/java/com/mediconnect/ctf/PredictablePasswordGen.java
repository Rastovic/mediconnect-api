package com.mediconnect.ctf;

import java.util.Random;

/**
 * [A04 #153] Shared, deliberately-predictable reset-password generator.
 *
 * The admin reset flow seeds {@link java.util.Random} with a value that is a
 * pure function of the target user id ({@code SEED_BASE + id}) - not
 * SecureRandom, not time. Anyone who knows the (public) user id can reproduce
 * the exact 12-char sequence offline with the same seed and hijack the account.
 *
 * Both the generator (AdminUserService#resetPassword) and the detector
 * (AuthService#login) call this, so "the password a login used equals the one
 * the predictable RNG would produce for that id" is decidable server-side.
 */
public final class PredictablePasswordGen {

    private PredictablePasswordGen() {}

    // Public, predictable seed offset — documented as part of the challenge.
    public static final long SEED_BASE = 1_000_000L;

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** Deterministic reset password for a given user id. */
    public static String forUser(long userId) {
        Random r = new Random(SEED_BASE + userId);
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
