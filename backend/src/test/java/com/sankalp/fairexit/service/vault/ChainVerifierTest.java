package com.sankalp.fairexit.service.vault;

import static org.assertj.core.api.Assertions.assertThat;

import com.sankalp.fairexit.service.vault.ChainVerifier.Problem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChainVerifierTest {

    private static final UUID TENANCY = UUID.randomUUID();
    private static final Instant T = Instant.parse("2026-11-17T10:00:00.123Z");

    private final List<ChainVerifier.Record> chain = new ArrayList<>();
    private final Map<UUID, String> files = new HashMap<>();

    /** Builds a valid chain of three records, like VaultService does. */
    @BeforeEach
    void buildChain() {
        String prev = ChainHash.GENESIS;
        for (int seq = 1; seq <= 3; seq++) {
            UUID id = UUID.randomUUID();
            String fileHash = ("f" + seq).repeat(32);
            ChainHash.Fields fields = new ChainHash.Fields(prev, seq, id, TENANCY, "PHOTO", "MOVE_IN",
                    "Bedroom " + seq, "Wall", seq == 3 ? new BigDecimal("3200") : null, fileHash, 1000L * seq,
                    T.plusSeconds(seq));
            String hash = ChainHash.of(fields);
            chain.add(new ChainVerifier.Record(fields, hash));
            files.put(id, fileHash);
            prev = hash;
        }
    }

    private List<ChainVerifier.Break> verify() {
        return ChainVerifier.verify(chain, files, chain.size(), chain.get(chain.size() - 1).storedRecordHash());
    }

    private ChainVerifier.Record withFields(ChainVerifier.Record r, ChainHash.Fields f) {
        return new ChainVerifier.Record(f, r.storedRecordHash());
    }

    @Test
    void anUntouchedChainIsValid() {
        assertThat(verify()).isEmpty();
    }

    @Test
    void anEmptyChainIsValid() {
        assertThat(ChainVerifier.verify(List.of(), Map.of(), 0, null)).isEmpty();
    }

    @Test
    void aChangedFileIsFound() {
        UUID second = chain.get(1).fields().documentId();
        files.put(second, "e".repeat(64));

        assertThat(verify()).singleElement().satisfies(b -> {
            assertThat(b.problem()).isEqualTo(Problem.FILE_CHANGED);
            assertThat(b.documentId()).isEqualTo(second);
            assertThat(b.chainSeq()).isEqualTo(2);
        });
    }

    @Test
    void aMissingFileIsFound() {
        files.remove(chain.get(0).fields().documentId());

        assertThat(verify()).extracting(ChainVerifier.Break::problem).containsExactly(Problem.FILE_MISSING);
    }

    @Test
    void aChangedRowIsFound() {
        // Someone edits the room tag in the database: the record hash no longer matches
        ChainVerifier.Record r = chain.get(1);
        ChainHash.Fields f = r.fields();
        chain.set(1, withFields(r, new ChainHash.Fields(f.prevHash(), f.chainSeq(), f.documentId(), f.tenancyId(),
                f.type(), f.phase(), "Kitchen", f.item(), f.amount(), f.fileSha256(), f.sizeBytes(), f.uploadedAt())));

        assertThat(verify()).extracting(ChainVerifier.Break::problem).containsExactly(Problem.RECORD_CHANGED);
    }

    @Test
    void aRewrittenRowWithAFreshHashStillBreaksTheNextLink() {
        // A smarter attacker also recomputes record 2's hash. Record 3 still points at the old one.
        ChainVerifier.Record r = chain.get(1);
        ChainHash.Fields f = r.fields();
        ChainHash.Fields forged = new ChainHash.Fields(f.prevHash(), f.chainSeq(), f.documentId(), f.tenancyId(),
                f.type(), f.phase(), "Kitchen", f.item(), f.amount(), f.fileSha256(), f.sizeBytes(), f.uploadedAt());
        chain.set(1, new ChainVerifier.Record(forged, ChainHash.of(forged)));

        assertThat(verify()).extracting(ChainVerifier.Break::problem)
                .contains(Problem.CHAIN_BROKEN)
                .doesNotContain(Problem.RECORD_CHANGED);
    }

    @Test
    void aRemovedRecordInTheMiddleIsFound() {
        chain.remove(1);

        assertThat(ChainVerifier.verify(chain, files, 3, chain.get(1).storedRecordHash()))
                .extracting(ChainVerifier.Break::problem)
                .contains(Problem.CHAIN_BROKEN, Problem.HEAD_MISMATCH);
    }

    @Test
    void aRemovedLastRecordIsFoundThroughTheChainHead() {
        String head = chain.get(2).storedRecordHash();
        chain.remove(2);

        assertThat(ChainVerifier.verify(chain, files, 3, head))
                .extracting(ChainVerifier.Break::problem).containsExactly(Problem.HEAD_MISMATCH);
    }

    @Test
    void theHashIsTheSameForAmountsWithOrWithoutDecimals() {
        ChainHash.Fields f = chain.get(2).fields();
        ChainHash.Fields withScale = new ChainHash.Fields(f.prevHash(), f.chainSeq(), f.documentId(), f.tenancyId(),
                f.type(), f.phase(), f.room(), f.item(), new BigDecimal("3200.00"), f.fileSha256(), f.sizeBytes(),
                f.uploadedAt());

        assertThat(ChainHash.of(withScale)).isEqualTo(ChainHash.of(f));
    }
}
