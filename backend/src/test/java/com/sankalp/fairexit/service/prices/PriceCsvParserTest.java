package com.sankalp.fairexit.service.prices;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.PriceSource;
import org.junit.jupiter.api.Test;

class PriceCsvParserTest {

    @Test
    void readsRowsAndCleansTheCity() {
        PriceCsvParser.Result r = PriceCsvParser.parse("""
                category,city,flat_size,amount,source
                cleaning,  bengaluru ,TWO_BHK,4500
                REPAIR,Navi mumbai,one_bhk,1200.50,user

                """);

        assertThat(r.ok()).isTrue();
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0)).isEqualTo(new PriceCsvParser.Row(DeductionCategory.CLEANING, "Bengaluru",
                FlatSize.TWO_BHK, new java.math.BigDecimal("4500"), PriceSource.COLLECTED));
        assertThat(r.rows().get(1).city()).isEqualTo("Navi Mumbai");
        assertThat(r.rows().get(1).source()).isEqualTo(PriceSource.USER);
    }

    @Test
    void everyBadRowIsReportedWithItsLineNumber() {
        PriceCsvParser.Result r = PriceCsvParser.parse("""
                category,city,flat_size,amount
                CLEANING,Bengaluru,TWO_BHK,4500
                MAGIC,Bengaluru,TWO_BHK,4500
                CLEANING,Bengaluru,TWO_BHK,lots
                CLEANING,Bengaluru,TWO_BHK,-1
                CLEANING,Bengaluru
                """);

        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).hasSize(4);
        assertThat(r.errors().get(0)).startsWith("Line 3:");
        assertThat(r.errors().get(1)).isEqualTo("Line 4: amount is not a number");
        assertThat(r.errors().get(2)).startsWith("Line 5:").contains("positive");
        assertThat(r.errors().get(3)).startsWith("Line 6:").contains("expected 4 or 5 fields");
    }

    @Test
    void theHeaderIsRequired() {
        assertThat(PriceCsvParser.parse("CLEANING,Bengaluru,TWO_BHK,4500").errors())
                .singleElement().asString().startsWith("Line 1:");
    }
}
