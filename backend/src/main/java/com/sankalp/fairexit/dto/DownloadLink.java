package com.sankalp.fairexit.dto;

import java.time.Instant;

public record DownloadLink(String url, Instant expiresAt) {
}
