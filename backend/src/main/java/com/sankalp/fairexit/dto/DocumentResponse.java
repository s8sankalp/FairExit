package com.sankalp.fairexit.dto;

import com.sankalp.fairexit.model.Document;
import com.sankalp.fairexit.model.DocumentPhase;
import com.sankalp.fairexit.model.DocumentStatus;
import com.sankalp.fairexit.model.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Everything a tenant needs to check a record themselves, including its hashes. */
public record DocumentResponse(UUID id, DocumentType type, DocumentPhase phase, DocumentStatus status,
                               String fileName, String contentType, long sizeBytes, String room, String item,
                               BigDecimal amount, String sha256, Integer chainSeq, String prevHash,
                               String recordHash, Instant uploadedAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getType(), d.getPhase(), d.getStatus(), d.getFileName(),
                d.getContentType(), d.getSizeBytes(), d.getRoom(), d.getItem(), d.getAmount(), d.getSha256(),
                d.getChainSeq(), d.getPrevHash(), d.getRecordHash(), d.getUploadedAt());
    }
}
