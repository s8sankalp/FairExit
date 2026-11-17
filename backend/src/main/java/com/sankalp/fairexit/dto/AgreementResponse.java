package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.Agreement;
import com.sankalp.fairexit.model.AgreementStatus;
import com.sankalp.fairexit.model.Clause;
import com.sankalp.fairexit.model.ClauseType;
import com.sankalp.fairexit.model.QuoteSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param droppedClauses clauses the AI suggested whose quote was not found in the agreement
 */
public record AgreementResponse(UUID id, UUID documentId, AgreementStatus status, String error,
                                int droppedClauses, List<ClauseView> clauses, Instant createdAt) {

    public record ClauseView(ClauseType type, Integer page, String quote, Map<String, Object> value,
                             QuoteSource checkedAgainst) {

        static ClauseView from(Clause c) {
            return new ClauseView(c.getType(), c.getPage(), c.getQuote(), c.getValue(), c.getCheckedAgainst());
        }
    }

    public static AgreementResponse from(Agreement a) {
        return new AgreementResponse(a.getId(), a.getDocumentId(), a.getStatus(), a.getError(),
                a.getDroppedClauses(), a.getClauses().stream().map(ClauseView::from).toList(), a.getCreatedAt());
    }
}
