package com.sankalp.fairexit.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankalp.fairexit.model.ClauseType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClauseParserTest {

    private final ClauseParser parser = new ClauseParser(new ObjectMapper());

    @Test
    void readsChargeAndValueClauses() {
        ClauseExtraction result = parser.parse("""
                {"pages":[{"page":1,"text":"page one text"}],
                 "clauses":[
                   {"type":"CLEANING","page":2,"quote":"The tenant pays normal cleaning.","tenantPays":true},
                   {"type":"deposit","page":1,"quote":"Deposit of Rs 1,50,000.","value":150000},
                   {"type":"NOTICE_PERIOD","quote":"One month notice.","value":30}]}
                """);

        assertThat(result.pages()).containsExactly("page one text");
        assertThat(result.clauses()).hasSize(3);
        assertThat(result.clauses().get(0).type()).isEqualTo(ClauseType.CLEANING);
        assertThat(result.clauses().get(0).value()).containsEntry("tenantPays", true);
        assertThat(result.clauses().get(1).type()).isEqualTo(ClauseType.DEPOSIT);
        assertThat((BigDecimal) result.clauses().get(1).value().get("amount")).isEqualByComparingTo("150000");
        assertThat(result.clauses().get(2).page()).isNull();
        assertThat(result.clauses().get(2).value()).containsKey("days");
    }

    @Test
    void badClausesAreSkippedNotFatal() {
        ClauseExtraction result = parser.parse("""
                {"clauses":[
                   {"type":"MAGIC","quote":"x x x x x x x x"},
                   {"type":"PAINTING","quote":""},
                   {"type":"PAINTING","quote":"Tenant repaints the flat.","tenantPays":"maybe"},
                   {"type":"REPAIRS","quote":"Tenant repairs any damage caused.","tenantPays":true}]}
                """);

        assertThat(result.clauses()).singleElement()
                .satisfies(c -> assertThat(c.type()).isEqualTo(ClauseType.REPAIRS));
        assertThat(result.pages()).isEmpty();
    }

    @Test
    void theWrongShapeIsABadAnswerAndIsRetried() {
        assertThatThrownBy(() -> parser.parse("The agreement says the tenant pays"))
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.BAD_OUTPUT));
        assertThatThrownBy(() -> parser.parse("{\"pages\":[]}"))
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.BAD_OUTPUT));
    }
}
