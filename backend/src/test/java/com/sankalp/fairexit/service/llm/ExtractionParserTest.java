package com.sankalp.fairexit.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.service.llm.ExtractionResult.ExtractedLine;
import com.sankalp.fairexit.service.llm.LlmException.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractionParserTest {

    private final ExtractionParser parser = new ExtractionParser(new ObjectMapper());

    private static String oneLine(String amountJson) {
        return "{\"lines\":[{\"item\":\"Painting\",\"category\":\"PAINTING\",\"amount\":" + amountJson + "}]}";
    }

    private void assertKind(String answer, Kind kind) {
        assertThatThrownBy(() -> parser.parse(answer))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(kind));
    }

    @Test
    void aValidAnswerBecomesLines() {
        ExtractionResult result = parser.parse("""
                {"lines":[
                  {"item":" Full flat repainting ","category":"PAINTING","amount":55000,"room":null},
                  {"item":"Broken tap","category":"repair","amount":1500.5,"room":"Bathroom"}],
                 "statedTotal":56500.5}
                """);

        assertThat(result.lines()).hasSize(2);
        ExtractedLine first = result.lines().get(0);
        assertThat(first.item()).isEqualTo("Full flat repainting");
        assertThat(first.category()).isEqualTo(DeductionCategory.PAINTING);
        assertThat(first.amount()).isEqualByComparingTo("55000");
        assertThat(first.room()).isNull();
        assertThat(result.lines().get(1).category()).isEqualTo(DeductionCategory.REPAIR);
        assertThat(result.lines().get(1).room()).isEqualTo("Bathroom");
        assertThat(result.statedTotal()).isEqualByComparingTo("56500.50");
    }

    @Test
    void statedTotalIsOptional() {
        assertThat(parser.parse(oneLine("100")).statedTotal()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"55,000\"", "\"₹55,000\"", "\"Rs. 55000\"", "55000.00"})
    void amountsWrittenAsTextStillParse(String amountJson) {
        assertThat(parser.parse(oneLine(amountJson)).lines().get(0).amount()).isEqualByComparingTo("55000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"a lot\"", "-5", "0", "null", "true", "99999999999"})
    void badAmountsRejectTheWholeAnswer(String amountJson) {
        assertKind(oneLine(amountJson), Kind.BAD_OUTPUT);
    }

    @Test
    void anUnknownCategoryBecomesOther() {
        ExtractionResult result = parser.parse(
                "{\"lines\":[{\"item\":\"Key charge\",\"category\":\"KEYS\",\"amount\":500}]}");

        assertThat(result.lines().get(0).category()).isEqualTo(DeductionCategory.OTHER);
    }

    @Test
    void aCodeFenceAroundTheJsonIsTolerated() {
        assertThat(parser.parse("```json\n" + oneLine("100") + "\n```").lines()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Sure! Here are the lines",                       // not JSON
            "[1,2,3]",                                        // not an object
            "{\"items\":[]}",                                 // no lines array
            "{\"lines\":[\"Painting 55000\"]}",               // line is not an object
            "{\"lines\":[{\"category\":\"PAINTING\",\"amount\":5}]}",   // no item
            "{\"lines\":[{\"item\":\" \",\"category\":\"PAINTING\",\"amount\":5}]}"})
    void wrongShapesAreRejected(String answer) {
        assertKind(answer, Kind.BAD_OUTPUT);
    }

    @Test
    void noLinesIsItsOwnResultAndIsNotRetried() {
        assertKind("{\"lines\":[],\"statedTotal\":null}", Kind.NO_LINES);
        assertThat(new LlmException(Kind.NO_LINES, "x").retryable()).isFalse();
        assertThat(new LlmException(Kind.BAD_OUTPUT, "x").retryable()).isTrue();
    }

    @Test
    void moreThanFiftyLinesIsRejected() {
        String line = "{\"item\":\"x\",\"category\":\"OTHER\",\"amount\":1}";
        String answer = "{\"lines\":[" + String.join(",", java.util.Collections.nCopies(51, line)) + "]}";

        assertKind(answer, Kind.BAD_OUTPUT);
    }
}
