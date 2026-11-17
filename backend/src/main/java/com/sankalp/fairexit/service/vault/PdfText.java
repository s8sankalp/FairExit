package com.sankalp.fairexit.service.vault;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the text layer of a PDF with OpenPDF. A scanned PDF has no text layer
 * (each page is a picture), so it is sent to the LLM as a file instead (spec decision 18).
 */
public final class PdfText {

    private static final Logger log = LoggerFactory.getLogger(PdfText.class);

    /** Fewer letters than this across the whole PDF means it is a scan. */
    static final int MIN_LETTERS = 100;

    private PdfText() {
    }

    /** Text of each page, or an empty list when the PDF cannot be read. */
    public static List<String> pages(byte[] pdf) {
        List<String> pages = new ArrayList<>();
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                pages.add(extractor.getTextFromPage(page));
            }
            return pages;
        } catch (IOException | RuntimeException e) {
            log.info("PDF has no readable text layer: {}", e.getMessage());
            return List.of();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public static boolean hasTextLayer(List<String> pages) {
        long letters = pages.stream().flatMapToInt(String::chars).filter(Character::isLetter).count();
        return letters >= MIN_LETTERS;
    }
}
