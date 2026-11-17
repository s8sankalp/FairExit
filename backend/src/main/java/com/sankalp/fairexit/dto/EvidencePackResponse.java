package com.sankalp.fairexit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param url        a link to the PDF that anyone can open until expiresAt (7 days)
 * @param sha256     hash of the PDF itself, so a copy can be checked later
 * @param chainValid the vault check result printed in the pack
 */
public record EvidencePackResponse(UUID id, String url, Instant expiresAt, String sha256, boolean chainValid) {
}
