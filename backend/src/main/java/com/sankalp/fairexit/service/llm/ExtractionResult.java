package com.sankalp.fairexit.service.llm;

import com.sankalp.fairexit.model.DeductionCategory;
import java.math.BigDecimal;
import java.util.List;

/**
 * What the AI read from a bill, after our checks. These are only suggestions:
 * the tenant confirms or edits every line before an audit can run.
 *
 * @param statedTotal the total written in the message, or null when there is none
 */
public record ExtractionResult(List<ExtractedLine> lines, BigDecimal statedTotal) {

    public ExtractionResult {
        lines = List.copyOf(lines);
    }

    public record ExtractedLine(String item, DeductionCategory category, BigDecimal amount, String room) {
    }
}
