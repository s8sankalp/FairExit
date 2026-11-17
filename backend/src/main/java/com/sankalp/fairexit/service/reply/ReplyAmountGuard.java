package com.sankalp.fairexit.service.reply;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The safety net for "amounts come only from the audit". It finds every money amount
 * in a draft (₹1,50,000, Rs. 2000, INR 500) and checks each one is an amount the audit
 * produced. A draft with any other amount is refused, never sent to the user.
 *
 * Today the drafts are built from templates, so this always passes. It is written to
 * stay true if an LLM is later allowed to reword the sentences around the amounts.
 */
public final class ReplyAmountGuard {

    private static final Pattern MONEY =
            Pattern.compile("(?i)(?:₹|\\brs\\.?|\\binr)\\s?(\\d[\\d,]*(?:\\.\\d+)?)");

    private ReplyAmountGuard() {
    }

    /** All money amounts written in the text, in order. */
    public static List<BigDecimal> amountsIn(String text) {
        List<BigDecimal> found = new ArrayList<>();
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            found.add(new BigDecimal(m.group(1).replace(",", "")));
        }
        return found;
    }

    /** Throws when the text holds an amount that is not in the allowed set. */
    public static void check(String text, Collection<BigDecimal> allowed) {
        for (BigDecimal amount : amountsIn(text)) {
            boolean known = allowed.stream().anyMatch(a -> a.compareTo(amount) == 0);
            if (!known) {
                throw new IllegalStateException("Draft contains an amount that is not in the audit: " + amount);
            }
        }
    }
}
