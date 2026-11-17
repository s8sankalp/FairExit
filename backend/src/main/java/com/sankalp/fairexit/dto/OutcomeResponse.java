package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.Outcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** percentReturned is worked out from the deposit, for the tenant and for the later building score. */
public record OutcomeResponse(UUID tenancyId, BigDecimal amountReturned, BigDecimal deposit, BigDecimal percentReturned,
                              int daysTaken, boolean verified, int remindersCancelled, Instant updatedAt) {

    public static OutcomeResponse from(Outcome o, BigDecimal deposit, int remindersCancelled) {
        BigDecimal percent = o.getAmountReturned().multiply(BigDecimal.valueOf(100))
                .divide(deposit, 1, java.math.RoundingMode.HALF_UP);
        return new OutcomeResponse(o.getTenancyId(), o.getAmountReturned(), deposit, percent, o.getDaysTaken(),
                o.isVerified(), remindersCancelled, o.getUpdatedAt());
    }
}
