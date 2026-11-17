package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.service.vault.ChainVerifier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** valid is true only when every file and every record checks out. */
public record VerifyResponse(UUID tenancyId, boolean valid, int documentsChecked, String chainHead,
                             List<ChainVerifier.Break> breaks, Instant checkedAt) {
}
