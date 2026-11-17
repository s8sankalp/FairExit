package com.sankalp.fairexit.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** One spelling per city, so tenancies and price data for the same city always match. */
public final class Cities {

    private Cities() {
    }

    /** "  navi   MUMBAI " becomes "Navi Mumbai". */
    public static String normalise(String city) {
        return Arrays.stream(city.trim().split("\\s+"))
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
