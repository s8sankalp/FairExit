package com.sankalp.fairexit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Three ways to add a bill. Send exactly one:
 *  lines      typed by hand (201, ready to audit)
 *  text       the landlord's message, pasted (202, read by the AI)
 *  documentId a photo or PDF of the bill already stored in the vault (202, read by the AI)
 */
public record CreateBillRequest(
        @Size(min = 1, max = 50) List<@Valid @NotNull BillLineRequest> lines,
        @Size(max = 5000) String text,
        UUID documentId) {

    @JsonIgnore
    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    @JsonIgnore
    public boolean hasDocument() {
        return documentId != null;
    }

    /** Cross field rule, reported as the field "oneSource". */
    @JsonIgnore
    @AssertTrue(message = "send exactly one of lines, text or documentId")
    public boolean isOneSource() {
        return Stream.of(lines != null, hasText(), hasDocument()).filter(Boolean::booleanValue).count() == 1;
    }
}
