package com.sankalp.fairexit.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    private String mask(String text) {
        return masker.mask(text, List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "+91 9876543210", "+919876543210", "09876543210",
            "+91 98765 43210", "91-98765-43210"})
    void phoneNumbersAreMasked(String phone) {
        assertThat(mask("Call me on " + phone + " today")).isEqualTo("Call me on [PHONE] today");
    }

    @Test
    void emailsAreMasked() {
        assertThat(mask("Mail owner.flat12@example.co.in please")).isEqualTo("Mail [EMAIL] please");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PIN 560034", "pincode: 560034", "Pin code - 560 034", "postal code 682022"})
    void pinCodesNextToTheWordPinAreMasked(String pin) {
        assertThat(mask("Flat 12, Koramangala, " + pin)).doesNotContain("560").doesNotContain("682")
                .contains("[PIN]");
    }

    /** The most important test here: masking must never destroy an amount. */
    @ParameterizedTest
    @ValueSource(strings = {
            "Painting 55,000", "Painting 55000", "Deposit 150000", "Painting - 150000",
            "Cleaning Rs. 8000", "Repair ₹1,500.50", "Painting 80000 Cleaning 15000", "Total 1,50,000"})
    void amountsAreNeverMasked(String line) {
        assertThat(mask(line)).isEqualTo(line);
    }

    @Test
    void theUsersNameIsMaskedInAnyCaseAsWholeWords() {
        String masked = masker.mask("Dear SANKALP suman, Mr. Suman's deposit. Summary below.",
                List.of("Sankalp Suman"));

        assertThat(masked).isEqualTo("Dear [NAME] [NAME], Mr. [NAME]'s deposit. Summary below.");
    }

    @Test
    void veryShortNamePartsAreNotMasked() {
        // "Al" inside other words or as a word is too risky to mask
        assertThat(masker.mask("Al paid all dues", List.of("Al"))).isEqualTo("Al paid all dues");
    }

    @Test
    void everythingTogether() {
        String masked = masker.mask(
                "Hi Asha, painting 55,000 and lift 2000. Call 9876543210 or a@b.com. PIN 560034.",
                List.of("Asha"));

        assertThat(masked).isEqualTo(
                "Hi [NAME], painting 55,000 and lift 2000. Call [PHONE] or [EMAIL]. PIN [PIN].");
    }
}
