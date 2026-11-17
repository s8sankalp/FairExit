package com.sankalp.fairexit.service.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PdfTextTest {

    private static final String PAGE_1 = "This rent agreement is made at Bengaluru between the owner and the tenant. "
            + "The tenant shall pay a security deposit of Rs. 1,50,000.";
    private static final String PAGE_2 = "The tenant shall bear the cost of normal cleaning at the time of vacating.";

    @Test
    void readsTheTextOfEachPage() {
        List<String> pages = PdfText.pages(TestFiles.pdf(List.of(PAGE_1, PAGE_2)));

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).contains("security deposit");
        assertThat(pages.get(1)).contains("normal cleaning");
        assertThat(PdfText.hasTextLayer(pages)).isTrue();
    }

    @Test
    void aPdfWithAlmostNoTextCountsAsAScan() {
        assertThat(PdfText.hasTextLayer(PdfText.pages(TestFiles.pdf(List.of("Page 1"))))).isFalse();
    }

    @Test
    void somethingThatIsNotAPdfGivesNoPages() {
        assertThat(PdfText.pages(TestFiles.png())).isEmpty();
    }
}
