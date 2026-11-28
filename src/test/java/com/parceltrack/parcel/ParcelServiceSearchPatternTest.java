package com.parceltrack.parcel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelServiceSearchPatternTest {

    @Test
    void plainTextBecomesALowerCaseContainsPattern() {
        assertThat(ParcelService.likePattern("Ali")).isEqualTo("%ali%");
    }

    @Test
    void likeWildcardsTypedByTheUserAreEscapedSoTheyMatchLiterally() {
        assertThat(ParcelService.likePattern("50%_!")).isEqualTo("%50!%!_!!%");
    }

    @Test
    void sqlInjectionTextIsJustText() {
        // It is also bound as a parameter, so it is never part of the SQL. This only checks the pattern builder.
        assertThat(ParcelService.likePattern("' OR '1'='1")).isEqualTo("%' or '1'='1%");
    }
}
