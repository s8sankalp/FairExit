package com.sankalp.fairexit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Where and how to upload: PUT the file bytes to uploadUrl with exactly these headers,
 * before expiresAt, then call the complete endpoint.
 */
public record UploadTicket(UUID documentId, String method, String uploadUrl,
                           Map<String, String> headers, Instant expiresAt) {
}
