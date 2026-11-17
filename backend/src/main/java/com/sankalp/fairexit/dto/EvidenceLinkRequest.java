package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.EvidenceObservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The full list of photos for one line. It replaces the line's links; an empty list removes them. */
public record EvidenceLinkRequest(@NotNull @Size(max = 20) List<@Valid @NotNull Link> links) {

    public record Link(@NotNull UUID documentId, @NotNull EvidenceObservation observation) {
    }
}
