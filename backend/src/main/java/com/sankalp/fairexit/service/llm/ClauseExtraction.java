package com.sankalp.fairexit.service.llm;

import com.sankalp.fairexit.model.ClauseType;
import java.util.List;
import java.util.Map;

/**
 * What the model read from an agreement, before the quote check.
 *
 * @param pages the model's own transcription, one entry per page. Only used for photos
 *              and scans; for a PDF with a text layer the real text is used instead.
 */
public record ClauseExtraction(List<RawClause> clauses, List<String> pages) {

    public ClauseExtraction {
        clauses = List.copyOf(clauses);
        pages = List.copyOf(pages);
    }

    public record RawClause(ClauseType type, Integer page, String quote, Map<String, Object> value) {
    }
}
