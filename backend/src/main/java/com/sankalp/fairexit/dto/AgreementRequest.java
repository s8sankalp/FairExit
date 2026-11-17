package com.sankalp.fairexit.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Read this stored AGREEMENT document. */
public record AgreementRequest(@NotNull UUID documentId) {
}
