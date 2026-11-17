package com.sankalp.fairexit.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.service.llm.ExtractionResult.ExtractedLine;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The guard between the LLM and our data. The model's answer is untrusted input:
 * it must be the exact JSON shape we asked for, and every amount must be a real
 * positive number, or the whole answer is thrown away and the call is retried.
 */
@Component
public class ExtractionParser {

    static final int MAX_LINES = 50;
    private static final int MAX_ITEM = 200;
    private static final int MAX_ROOM = 60;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final ObjectMapper objectMapper;

    public ExtractionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExtractionResult parse(String answer) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stripCodeFence(answer));
        } catch (JsonProcessingException e) {
            throw bad("answer is not JSON");
        }
        if (root == null || !root.isObject() || !root.path("lines").isArray()) {
            throw bad("answer has no \"lines\" array");
        }
        JsonNode linesNode = root.path("lines");
        if (linesNode.isEmpty()) {
            throw new LlmException(Kind.NO_LINES, "No deduction lines found");
        }
        if (linesNode.size() > MAX_LINES) {
            throw bad("more than " + MAX_LINES + " lines");
        }

        List<ExtractedLine> lines = new ArrayList<>();
        for (JsonNode node : linesNode) {
            lines.add(line(node, lines.size() + 1));
        }
        JsonNode total = root.path("statedTotal");
        BigDecimal statedTotal = total.isMissingNode() || total.isNull() ? null : amount(total, "statedTotal");
        return new ExtractionResult(lines, statedTotal);
    }

    private static ExtractedLine line(JsonNode node, int number) {
        if (!node.isObject()) {
            throw bad("line " + number + " is not an object");
        }
        String item = node.path("item").isTextual() ? node.path("item").asText().trim() : "";
        if (item.isEmpty()) {
            throw bad("line " + number + " has no item");
        }
        String room = node.path("room").isTextual() ? node.path("room").asText().trim() : "";
        return new ExtractedLine(
                cut(item, MAX_ITEM),
                category(node.path("category")),
                amount(node.path("amount"), "line " + number + " amount"),
                room.isEmpty() ? null : cut(room, MAX_ROOM));
    }

    /** An unknown category is not fatal: it becomes OTHER and the tenant can fix it. */
    private static DeductionCategory category(JsonNode node) {
        try {
            return DeductionCategory.valueOf(node.asText("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DeductionCategory.OTHER;
        }
    }

    /** Accepts 55000, 55000.50 and strings like "55,000" or "₹55,000". Anything else is rejected. */
    private static BigDecimal amount(JsonNode node, String what) {
        BigDecimal value;
        try {
            if (node.isNumber()) {
                value = node.decimalValue();
            } else if (node.isTextual()) {
                value = new BigDecimal(node.asText().replaceAll("[₹,\\s]|(?i)rs\\.?|(?i)inr", ""));
            } else {
                throw bad(what + " is missing");
            }
        } catch (NumberFormatException e) {
            throw bad(what + " is not a number");
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw bad(what + " is out of range");
        }
        return value;
    }

    private static String stripCodeFence(String answer) {
        String text = answer == null ? "" : answer.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return text;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static LlmException bad(String detail) {
        return new LlmException(Kind.BAD_OUTPUT, "Invalid extraction: " + detail);
    }
}
