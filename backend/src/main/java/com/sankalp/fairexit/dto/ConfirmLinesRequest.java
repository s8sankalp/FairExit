package com.sankalp.fairexit.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The full, final list of lines. It replaces whatever the bill had before. */
public record ConfirmLinesRequest(@NotNull @Size(min = 1, max = 50) List<@Valid @NotNull BillLineRequest> lines) {
}
