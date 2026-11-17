package com.sankalp.fairexit.service.llm;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * PRD rule: "each clause must quote text that exists in the source; else dropped".
 * Text is compared loosely (case, spacing, punctuation and line breaks ignored),
 * because PDF text layers break lines in odd places. The words themselves must match.
 */
public final class QuoteChecker {

    /** Shorter quotes ("yes", "Rs 500") could match almost anywhere, so they prove nothing. */
    static final int MIN_QUOTE_CHARS = 12;

    private QuoteChecker() {
    }

    /**
     * @param page  1 based page the model named, or null; when the page exists only it is searched
     * @param pages the source text, one entry per page
     */
    public static boolean isInSource(String quote, Integer page, List<String> pages) {
        String needle = normalise(quote);
        if (needle.length() < MIN_QUOTE_CHARS || pages.isEmpty()) {
            return false;
        }
        if (page != null && page >= 1 && page <= pages.size()) {
            return normalise(pages.get(page - 1)).contains(needle);
        }
        return pages.stream().anyMatch(p -> normalise(p).contains(needle));
    }

    static String normalise(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return plain.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
