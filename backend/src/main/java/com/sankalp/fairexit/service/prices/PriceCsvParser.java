package com.sankalp.fairexit.service.prices;

import com.sankalp.fairexit.model.DeductionCategory;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.PriceSource;
import com.sankalp.fairexit.service.Cities;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads an admin's price CSV. Hand written (no CSV library) because the format is tiny:
 *
 *   category,city,flat_size,amount[,source]
 *   CLEANING,Bengaluru,TWO_BHK,4500
 *
 * Every row is checked; if any row is wrong, nothing is imported (all or nothing),
 * and every problem is reported with its line number.
 */
public final class PriceCsvParser {

    public static final int MAX_ROWS = 10_000;
    private static final String HEADER = "category,city,flat_size,amount";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");

    public record Row(DeductionCategory category, String city, FlatSize flatSize, BigDecimal amount,
                      PriceSource source) {
    }

    public record Result(List<Row> rows, List<String> errors) {

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private PriceCsvParser() {
    }

    public static Result parse(String csv) {
        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String[] lines = (csv == null ? "" : csv).replace("\r", "").split("\n");

        if (lines.length == 0 || !lines[0].trim().toLowerCase(Locale.ROOT).startsWith(HEADER)) {
            return new Result(List.of(), List.of("Line 1: the header must be " + HEADER + "[,source]"));
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (rows.size() + errors.size() >= MAX_ROWS) {
                errors.add("More than " + MAX_ROWS + " rows; split the file");
                break;
            }
            int lineNo = i + 1;
            String[] f = line.split(",", -1);
            if (f.length < 4 || f.length > 5) {
                errors.add("Line " + lineNo + ": expected 4 or 5 fields, found " + f.length);
                continue;
            }
            try {
                DeductionCategory category = DeductionCategory.valueOf(f[0].trim().toUpperCase(Locale.ROOT));
                String city = f[1].trim();
                if (city.isEmpty() || city.length() > 60) {
                    throw new IllegalArgumentException("city must be 1 to 60 characters");
                }
                FlatSize flatSize = FlatSize.valueOf(f[2].trim().toUpperCase(Locale.ROOT));
                BigDecimal amount = new BigDecimal(f[3].trim());
                if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2) {
                    throw new IllegalArgumentException("amount must be a positive number with at most 2 decimals");
                }
                PriceSource source = f.length == 5 && !f[4].isBlank()
                        ? PriceSource.valueOf(f[4].trim().toUpperCase(Locale.ROOT)) : PriceSource.COLLECTED;
                rows.add(new Row(category, Cities.normalise(city), flatSize, amount, source));
            } catch (IllegalArgumentException e) { // includes NumberFormatException and bad enum names
                errors.add("Line " + lineNo + ": " + (e instanceof NumberFormatException
                        ? "amount is not a number" : e.getMessage()));
            }
        }
        return new Result(rows, errors);
    }
}
