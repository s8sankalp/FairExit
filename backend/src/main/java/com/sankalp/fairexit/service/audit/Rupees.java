package com.sankalp.fairexit.service.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats amounts the Indian way: 150000 becomes ₹1,50,000. */
public final class Rupees {

    private Rupees() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        String whole = scaled.toBigInteger().abs().toString();
        String paise = scaled.remainder(BigDecimal.ONE).abs().movePointRight(2).setScale(0).toPlainString();

        StringBuilder grouped = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            grouped.append(whole);
        } else {
            String head = whole.substring(0, len - 3);
            // After the last three digits, Indian grouping goes in twos
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(whole, len - 3, len);
        }

        String sign = scaled.signum() < 0 ? "-" : "";
        String decimals = "0".equals(paise) ? "" : "." + (paise.length() == 1 ? "0" + paise : paise);
        return sign + "₹" + grouped + decimals;
    }
}
