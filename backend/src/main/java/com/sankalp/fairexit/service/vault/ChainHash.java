package com.sankalp.fairexit.service.vault;

import com.sankalp.fairexit.service.Hashing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * The hash of one vault record. It covers the previous record's hash, so each record
 * seals everything before it: change any old file or row and every later hash is wrong.
 */
public final class ChainHash {

    /** prev_hash of the first record in a chain. */
    public static final String GENESIS = "0".repeat(64);

    private ChainHash() {
    }

    /** Every field that matters, in a fixed order, one per line. */
    public record Fields(String prevHash, int chainSeq, UUID documentId, UUID tenancyId, String type,
                         String phase, String room, String item, BigDecimal amount, String fileSha256,
                         long sizeBytes, Instant uploadedAt) {
    }

    public static String of(Fields f) {
        String canonical = String.join("\n",
                f.prevHash(),
                String.valueOf(f.chainSeq()),
                f.documentId().toString(),
                f.tenancyId().toString(),
                f.type(),
                f.phase(),
                nullToEmpty(f.room()),
                nullToEmpty(f.item()),
                // 3200 and 3200.00 must hash the same, as the database gives back scale 2
                f.amount() == null ? "" : f.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                f.fileSha256(),
                String.valueOf(f.sizeBytes()),
                f.uploadedAt().toString());
        return Hashing.sha256Hex(canonical);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
