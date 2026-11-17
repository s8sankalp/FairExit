package com.sankalp.fairexit.service.prices;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Median and 90th percentile of a group of prices, after dropping far outliers.
 * Pure Java, so it is easy to test. (At larger scale the same can be done inside
 * PostgreSQL with percentile_cont.)
 */
public final class PriceStatsCalculator {

    /** Far outlier fence: more than 3 interquartile ranges outside the middle half. */
    private static final double FENCE = 3.0;

    public record Result(BigDecimal p50, BigDecimal p90, int n) {
    }

    private PriceStatsCalculator() {
    }

    public static Optional<Result> calculate(List<BigDecimal> amounts) {
        if (amounts.isEmpty()) {
            return Optional.empty();
        }
        List<Double> sorted = amounts.stream().map(BigDecimal::doubleValue).sorted().toList();

        // A typo like 1500000 for 1500 would drag p90 up and hide overcharging, so drop it
        double q1 = percentile(sorted, 0.25);
        double q3 = percentile(sorted, 0.75);
        double iqr = q3 - q1;
        List<Double> kept = sorted.stream()
                .filter(a -> a >= q1 - FENCE * iqr && a <= q3 + FENCE * iqr)
                .toList();

        return Optional.of(new Result(money(percentile(kept, 0.5)), money(percentile(kept, 0.9)), kept.size()));
    }

    /** Linear interpolation between the closest ranks, the same as PostgreSQL percentile_cont. */
    static double percentile(List<Double> sorted, double p) {
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double rank = p * (sorted.size() - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        return sorted.get(low) + (rank - low) * (sorted.get(high) - sorted.get(low));
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
