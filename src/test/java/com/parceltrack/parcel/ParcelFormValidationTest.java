package com.parceltrack.parcel;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Server-side validation rules for the parcel registration form (master plan, M2 test cases). */
class ParcelFormValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static ParcelForm valid() {
        ParcelForm form = new ParcelForm();
        form.setRecipientName("Test Recipient");
        form.setRecipientPhone("0123456789");
        form.setRecipientAddress("1 Test Street, Test Town");
        form.setWeight(new BigDecimal("1.50"));
        return form;
    }

    private static long violationsOn(ParcelForm form, String field) {
        return validator.validate(form).stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .count();
    }

    @Test
    void aNormalFormIsAccepted() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void internationalPhoneWithPlusIsAccepted() {
        ParcelForm form = valid();
        form.setRecipientPhone("+60123456789");
        assertThat(violationsOn(form, "recipientPhone")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "12345", "0123 456 789", "01234567890123456", "012345678a", "<script>", "0123456789\n"})
    void invalidPhonesAreRejected(String phone) {
        ParcelForm form = valid();
        form.setRecipientPhone(phone);
        assertThat(violationsOn(form, "recipientPhone")).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "-0.01", "0", "0.00", "1000", "1.234", "99999999999"})
    void invalidWeightsAreRejected(String weight) {
        ParcelForm form = valid();
        form.setWeight(new BigDecimal(weight));
        assertThat(violationsOn(form, "weight")).isPositive();
    }

    @Test
    void missingWeightIsRejected() {
        ParcelForm form = valid();
        form.setWeight(null);
        assertThat(violationsOn(form, "weight")).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "12.5", "999.99"})
    void validWeightsAreAccepted(String weight) {
        ParcelForm form = valid();
        form.setWeight(new BigDecimal(weight));
        assertThat(violationsOn(form, "weight")).isZero();
    }

    @Test
    void overlongAddressIsRejectedButTheLimitItselfIsAccepted() {
        ParcelForm form = valid();
        form.setRecipientAddress("a".repeat(Parcel.MAX_ADDRESS));
        assertThat(violationsOn(form, "recipientAddress")).isZero();
        form.setRecipientAddress("a".repeat(Parcel.MAX_ADDRESS + 1));
        assertThat(violationsOn(form, "recipientAddress")).isPositive();
    }

    @Test
    void overlongNameIsRejected() {
        ParcelForm form = valid();
        form.setRecipientName("n".repeat(Parcel.MAX_NAME + 1));
        assertThat(violationsOn(form, "recipientName")).isPositive();
    }

    @Test
    void blankNameAndAddressAreRejected() {
        ParcelForm form = valid();
        form.setRecipientName("  ");
        form.setRecipientAddress("");
        assertThat(violationsOn(form, "recipientName")).isPositive();
        assertThat(violationsOn(form, "recipientAddress")).isPositive();
    }

    @Test
    void controlCharactersInNameAndAddressAreRejected() {
        ParcelForm form = valid();
        form.setRecipientName("Line one\nLine two");
        form.setRecipientAddress("Tab\there");
        assertThat(violationsOn(form, "recipientName")).isPositive();
        assertThat(violationsOn(form, "recipientAddress")).isPositive();
    }

    @Test
    void scriptTextIsNotRejectedHere_itIsEncodedWhenShown() {
        // XSS is stopped by output encoding (th:text), not by refusing the text. See the integration test.
        ParcelForm form = valid();
        form.setRecipientName("<script>alert(1)</script>");
        assertThat(violationsOn(form, "recipientName")).isZero();
    }
}
