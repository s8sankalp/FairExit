package com.sankalp.fairexit.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.model.ClauseType;
import com.sankalp.fairexit.service.llm.ClauseExtraction.RawClause;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Checks the shape of a clause answer. A clause with an unknown type, no quote or a
 * value of the wrong kind is skipped, not fatal: the other clauses are still useful.
 * Whether a quote is real is decided afterwards by QuoteChecker.
 */
@Component
public class ClauseParser {

    private static final int MAX_CLAUSES = 40;
    private static final int MAX_QUOTE = 1000;

    private final ObjectMapper objectMapper;

    public ClauseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ClauseExtraction parse(String answer) {
        JsonNode root;
        try {
            root = objectMapper.readTree(answer == null ? "" : answer.trim()
                    .replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", ""));
        } catch (JsonProcessingException e) {
            throw new LlmException(Kind.BAD_OUTPUT, "Invalid clause answer: not JSON");
        }
        if (root == null || !root.isObject() || !root.path("clauses").isArray()) {
            throw new LlmException(Kind.BAD_OUTPUT, "Invalid clause answer: no \"clauses\" array");
        }

        List<RawClause> clauses = new ArrayList<>();
        for (JsonNode node : root.path("clauses")) {
            if (clauses.size() == MAX_CLAUSES) {
                break;
            }
            RawClause clause = clause(node);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        List<String> pages = new ArrayList<>();
        for (JsonNode page : root.path("pages")) {
            pages.add(page.path("text").asText(""));
        }
        return new ClauseExtraction(clauses, pages);
    }

    private static RawClause clause(JsonNode node) {
        ClauseType type;
        try {
            type = ClauseType.valueOf(node.path("type").asText("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String quote = node.path("quote").asText("").trim();
        if (quote.isEmpty()) {
            return null;
        }
        if (quote.length() > MAX_QUOTE) {
            quote = quote.substring(0, MAX_QUOTE);
        }
        Integer page = node.path("page").canConvertToInt() && node.path("page").asInt() > 0
                ? node.path("page").asInt() : null;

        Map<String, Object> value = new HashMap<>();
        if (type.chargeCategory() != null) {
            if (!node.path("tenantPays").isBoolean()) {
                return null; // a charge clause must say who pays
            }
            value.put("tenantPays", node.path("tenantPays").asBoolean());
        } else {
            JsonNode number = node.path("value");
            if (number.isNumber() && number.asDouble() > 0) {
                String unit = switch (type) {
                    case DEPOSIT, RENT -> "amount";
                    case LOCK_IN -> "months";
                    default -> "days";
                };
                value.put(unit, number.decimalValue());
            }
        }
        return new RawClause(type, page, quote, value);
    }
}
