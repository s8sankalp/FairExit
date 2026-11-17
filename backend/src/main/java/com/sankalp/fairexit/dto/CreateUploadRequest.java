package com.sankalp.fairexit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.DocumentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Asks for an upload URL. The size limit (10 MB) and the allowed types are checked
 * in the service, because they come from configuration.
 *
 * @param amount only for UTILITY_BILL: the amount on the bill, used by audit rule 1
 */
public record CreateUploadRequest(
        @NotNull DocumentType type,
        DocumentPhase phase,
        @NotBlank @Size(max = 200) String fileName,
        @NotBlank @Size(max = 50) String contentType,
        @NotNull @Positive Long sizeBytes,
        @Size(max = 60) String room,
        @Size(max = 100) String item,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount) {

    @JsonIgnore
    @AssertTrue(message = "amount is required for a UTILITY_BILL and not allowed for other types")
    public boolean isAmountOnlyForUtilityBills() {
        return type == null || (type == DocumentType.UTILITY_BILL) == (amount != null);
    }
}
