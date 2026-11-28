package com.parceltrack.parcel;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingNumberGeneratorTest {

    private final TrackingNumberGenerator generator = new TrackingNumberGenerator();

    @Test
    void numbersHaveTheAgreedFormat() {
        for (int i = 0; i < 1_000; i++) {
            String number = generator.next();
            assertThat(number).matches("^PT-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$");
            assertThat(number.length()).isLessThanOrEqualTo(Parcel.MAX_TRACKING_NUMBER);
        }
    }

    @Test
    void tenThousandNumbersAreAllDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSize(10_000);
    }

    @Test
    void alphabetHasExactly32DistinctCharactersWithoutLookAlikes() {
        assertThat(TrackingNumberGenerator.ALPHABET).hasSize(32);
        assertThat(TrackingNumberGenerator.ALPHABET.chars().distinct().count()).isEqualTo(32);
        assertThat(TrackingNumberGenerator.ALPHABET).doesNotContain("I", "O", "0", "1");
    }

    @Test
    void normalizeAcceptsWellFormedNumbersInAnyCaseWithSpaces() {
        assertThat(TrackingNumberGenerator.normalize("  pt-abcd-2345-wxyz ")).isEqualTo("PT-ABCD-2345-WXYZ");
    }

    @Test
    void normalizeRejectsEverythingElse() {
        assertThat(TrackingNumberGenerator.normalize(null)).isNull();
        assertThat(TrackingNumberGenerator.normalize("")).isNull();
        assertThat(TrackingNumberGenerator.normalize("101")).isNull();
        assertThat(TrackingNumberGenerator.normalize("PT-ABCD-2345")).isNull();
        assertThat(TrackingNumberGenerator.normalize("PT-ABCD-2345-WXY0")).isNull(); // 0 is not in the alphabet
        assertThat(TrackingNumberGenerator.normalize("' OR '1'='1")).isNull();
        assertThat(TrackingNumberGenerator.normalize("PT-ABCD-2345-WXYZ\nPT-ABCD-2345-WXYZ")).isNull();
        assertThat(TrackingNumberGenerator.normalize("A".repeat(5_000))).isNull();
    }
}
