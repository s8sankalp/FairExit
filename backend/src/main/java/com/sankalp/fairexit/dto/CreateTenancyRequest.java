package com.sankalp.fairexit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sankalp.fairexit.model.FlatSize;
import com.sankalp.fairexit.model.IndianState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTenancyRequest(
        @NotBlank @Size(max = 60) String city,
        @NotNull IndianState state,
        @NotNull FlatSize flatSize,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal rent,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal deposit,
        @NotNull LocalDate startDate,
        LocalDate endDate,          // optional: still living there
        @Min(0) @Max(365) Integer noticeDays) { // optional: filled from the agreement later

    /** Cross field rule, reported as the field "endDateAfterStartDate". */
    @JsonIgnore
    @AssertTrue(message = "endDate must be after startDate")
    public boolean isEndDateAfterStartDate() {
        return endDate == null || startDate == null || endDate.isAfter(startDate);
    }
}
