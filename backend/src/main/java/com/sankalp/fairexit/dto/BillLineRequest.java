package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.DeductionCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillLineRequest(
        @NotBlank @Size(max = 200) String item,
        @NotNull DeductionCategory category,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 60) String room) {
}
