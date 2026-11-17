package com.sankalp.fairexit.service.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Re-checks a tenancy's chain from the first record to the last. Pure: it is given the
 * stored rows and the hashes of the files as they are now, and returns every break.
 */
public final class ChainVerifier {

    public enum Problem {
        /** The file in storage is not the file that was hashed. */
        FILE_CHANGED,
        /** The file is gone from storage. */
        FILE_MISSING,
        /** A field of the database row was changed after it was stored. */
        RECORD_CHANGED,
        /** A record was removed, inserted or reordered. */
        CHAIN_BROKEN,
        /** The chain is shorter, or ends on a different record, than the tenancy says. */
        HEAD_MISMATCH
    }

    public record Record(ChainHash.Fields fields, String storedRecordHash) {
    }

    public record Break(UUID documentId, Integer chainSeq, Problem problem, String detail) {
    }

    private ChainVerifier() {
    }

    /**
     * @param records    STORED documents ordered by chain_seq
     * @param fileHashes current SHA-256 of each document's file; no entry means the file is missing
     */
    public static List<Break> verify(List<Record> records, Map<UUID, String> fileHashes,
                                     int expectedLength, String expectedHead) {
        List<Break> breaks = new ArrayList<>();
        String previous = ChainHash.GENESIS;
        int expectedSeq = 1;

        for (Record r : records) {
            ChainHash.Fields f = r.fields();
            UUID id = f.documentId();

            if (f.chainSeq() != expectedSeq || !f.prevHash().equals(previous)) {
                breaks.add(new Break(id, f.chainSeq(), Problem.CHAIN_BROKEN,
                        "Expected record " + expectedSeq + " to follow the record before it"));
            }
            if (!ChainHash.of(f).equals(r.storedRecordHash())) {
                breaks.add(new Break(id, f.chainSeq(), Problem.RECORD_CHANGED,
                        "The stored details of this document were changed"));
            }
            Optional<String> current = Optional.ofNullable(fileHashes.get(id));
            if (current.isEmpty()) {
                breaks.add(new Break(id, f.chainSeq(), Problem.FILE_MISSING, "The file is missing from storage"));
            } else if (!current.get().equals(f.fileSha256())) {
                breaks.add(new Break(id, f.chainSeq(), Problem.FILE_CHANGED,
                        "The file was changed after upload (SHA-256 is now " + current.get() + ")"));
            }
            // Go on from the stored hash so that one bad record does not hide the rest
            previous = r.storedRecordHash();
            expectedSeq = f.chainSeq() + 1;
        }

        String head = records.isEmpty() ? null : records.get(records.size() - 1).storedRecordHash();
        if (records.size() != expectedLength || !java.util.Objects.equals(head, expectedHead)) {
            breaks.add(new Break(null, null, Problem.HEAD_MISMATCH,
                    "The tenancy expects " + expectedLength + " documents in its chain, found " + records.size()));
        }
        return breaks;
    }
}
