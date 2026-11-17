package com.sankalp.fairexit.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** @param receiptDocumentId optional: a stored RECEIPT (for example the bank credit) that proves the amount */
public record OutcomeRequest(
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amountReturned,
        @NotNull @Min(0) @Max(730) Integer daysTaken,
        UUID receiptDocumentId) {
}
