package com.sankalp.fairexit.service.prices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PriceStatsCalculatorTest {

    private static List<BigDecimal> amounts(int... values) {
        return IntStream.of(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    void medianAndP90MatchPostgresPercentileCont() {
        // 1000, 2000 ... 10000: percentile_cont(0.5) = 5500, percentile_cont(0.9) = 9100
        PriceStatsCalculator.Result r = PriceStatsCalculator.calculate(
                amounts(1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000)).orElseThrow();

        assertThat(r.p50()).isEqualByComparingTo("5500");
        assertThat(r.p90()).isEqualByComparingTo("9100");
        assertThat(r.n()).isEqualTo(10);
    }

    @Test
    void orderOfInputDoesNotMatter() {
        assertThat(PriceStatsCalculator.calculate(amounts(9000, 1000, 5000)))
                .isEqualTo(PriceStatsCalculator.calculate(amounts(1000, 5000, 9000)));
    }

    @Test
    void aTypoFarOutsideTheRangeIsDropped() {
        List<BigDecimal> prices = new ArrayList<>(amounts(1400, 1450, 1500, 1500, 1550, 1600, 1600, 1650, 1700, 1750));
        prices.add(BigDecimal.valueOf(1_500_000)); // someone typed three extra zeros

        PriceStatsCalculator.Result r = PriceStatsCalculator.calculate(prices).orElseThrow();

        assertThat(r.n()).isEqualTo(10);
        assertThat(r.p90()).isLessThan(new BigDecimal("2000"));
    }

    @Test
    void ordinarySpreadIsKept() {
        assertThat(PriceStatsCalculator.calculate(amounts(500, 800, 1000, 1200, 1500, 2000, 2500, 3000))
                .orElseThrow().n()).isEqualTo(8);
    }

    @Test
    void oneOrNoPrices() {
        assertThat(PriceStatsCalculator.calculate(amounts(1200)).orElseThrow().p90()).isEqualByComparingTo("1200");
        assertThat(PriceStatsCalculator.calculate(List.of())).isEmpty();
    }
}
