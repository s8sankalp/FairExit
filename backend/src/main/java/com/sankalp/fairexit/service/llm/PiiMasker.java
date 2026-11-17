package com.sankalp.fairexit.service.llm;

import java.util.Collection;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Hides personal details before any text is stored or sent to the LLM.
 *
 * Bills are full of numbers, so each pattern is written to never eat an amount:
 * a phone needs 10 digits starting 6 to 9 (no rupee amount on a bill looks like that),
 * and a PIN code is only masked next to the word "pin" (or similar), because a bare
 * six digit number such as 150000 is far more likely to be an amount.
 */
@Component
public class PiiMasker {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    // 9876543210, +91 9876543210, 09876543210, +91 98765 43210
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+?91[\\s-]?|0)?[6-9]\\d{9}|\\+?91[\\s-]?[6-9]\\d{4}[\\s-]\\d{5})(?!\\d)");

    // "PIN 560034", "pincode: 560 034", "Pin code - 682022"
    private static final Pattern PIN = Pattern.compile(
            "(?i)\\b(pin\\s?code|pin|postal\\s?code|zip)\\b(\\s*[:\\-]?\\s*)[1-9]\\d{2}\\s?\\d{3}(?!\\d)");

    private static final int MIN_NAME_PART = 3;

    /**
     * @param names names the user entered (their own name today); each part of
     *              3 letters or more is masked as a whole word, in any letter case
     */
    public String mask(String text, Collection<String> names) {
        String masked = EMAIL.matcher(text).replaceAll("[EMAIL]");
        masked = PHONE.matcher(masked).replaceAll("[PHONE]");
        masked = PIN.matcher(masked).replaceAll("$1$2[PIN]");
        for (String name : names) {
            if (name == null) {
                continue;
            }
            for (String part : name.trim().split("\\s+")) {
                if (part.length() >= MIN_NAME_PART) {
                    masked = Pattern.compile("\\b" + Pattern.quote(part) + "\\b",
                                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(masked).replaceAll("[NAME]");
                }
            }
        }
        return masked;
    }
}
