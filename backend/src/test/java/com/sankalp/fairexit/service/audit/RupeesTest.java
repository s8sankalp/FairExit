package com.sankalp.fairexit.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RupeesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0        | ₹0",
            "999      | ₹999",
            "3200     | ₹3,200",
            "150000   | ₹1,50,000",
            "1234567  | ₹12,34,567",
            "10.5     | ₹10.50",
            "10.05    | ₹10.05",
            "-2000    | -₹2,000"
    })
    void formatsTheIndianWay(String amount, String expected) {
        assertThat(Rupees.format(new BigDecimal(amount))).isEqualTo(expected);
    }
}
