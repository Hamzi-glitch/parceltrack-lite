package com.parceltrack.parcel;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates tracking numbers such as PT-7KQ2-M9XA-4RTD.
 *
 * The 12 random characters come from SecureRandom, 5 bits each, so a number carries 60 random
 * bits and cannot be guessed from another one. The database ID is never used. The alphabet
 * leaves out I, O, 0 and 1 so numbers are easy to read out loud.
 */
@Component
public class TrackingNumberGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // exactly 32 characters
    private static final int GROUPS = 3;
    private static final int GROUP_LENGTH = 4;

    /** Exactly the format {@link #next()} produces. Used to reject junk before touching the database. */
    private static final Pattern FORMAT = Pattern.compile("^PT-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$");

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder("PT");
        for (int group = 0; group < GROUPS; group++) {
            sb.append('-');
            for (int i = 0; i < GROUP_LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }

    /** Trims and upper-cases what a visitor typed. Returns null when it cannot be a tracking number. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        return FORMAT.matcher(cleaned).matches() ? cleaned : null;
    }
}
