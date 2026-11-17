package com.sankalp.fairexit.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteCheckerTest {

    private static final List<String> PAGES = List.of(
            "The Licensee shall pay a security\ndeposit of Rs. 1,50,000 (Rupees one lakh fifty thousand).",
            "The Licensee shall bear the cost of NORMAL cleaning at the time of vacating the premises.");

    @Test
    void anExactQuoteIsFound() {
        assertThat(QuoteChecker.isInSource(
                "The Licensee shall bear the cost of NORMAL cleaning at the time of vacating the premises.",
                2, PAGES)).isTrue();
    }

    @Test
    void caseSpacingLineBreaksAndPunctuationDoNotMatter() {
        assertThat(QuoteChecker.isInSource("the licensee shall pay a security deposit of Rs 1,50,000", 1, PAGES))
                .isTrue();
    }

    @Test
    void changedWordsAreNotFound() {
        // The model "improved" the wording: deep instead of normal. This clause must be dropped.
        assertThat(QuoteChecker.isInSource("The Licensee shall bear the cost of deep cleaning", 2, PAGES)).isFalse();
        // and an invented clause is never found
        assertThat(QuoteChecker.isInSource("The Licensee shall pay for repainting the flat", null, PAGES)).isFalse();
    }

    @Test
    void theNamedPageIsTheOnlyOneSearched() {
        assertThat(QuoteChecker.isInSource("shall bear the cost of normal cleaning", 1, PAGES)).isFalse();
        assertThat(QuoteChecker.isInSource("shall bear the cost of normal cleaning", null, PAGES)).isTrue();
        assertThat(QuoteChecker.isInSource("shall bear the cost of normal cleaning", 9, PAGES)).isTrue(); // no such page
    }

    @Test
    void veryShortQuotesProveNothing() {
        assertThat(QuoteChecker.isInSource("Rs. 1,50,000", 1, PAGES)).isFalse();
    }
}
