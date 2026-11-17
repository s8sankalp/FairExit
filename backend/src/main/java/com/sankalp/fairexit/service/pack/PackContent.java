package com.sankalp.fairexit.service.pack;

import com.sankalp.fairexit.dto.AgreementResponse;
import com.sankalp.fairexit.dto.AuditResponse;
import com.sankalp.fairexit.dto.DocumentResponse;
import com.sankalp.fairexit.dto.VerifyResponse;
import com.sankalp.fairexit.model.Tenancy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything printed in the pack, gathered before the PDF is drawn.
 *
 * @param agreement   null when no agreement was read
 * @param photoBytes  image bytes of the photos to embed, by document id
 */
public record PackContent(String tenantName, Tenancy tenancy, AuditResponse audit, AgreementResponse agreement,
                          List<DocumentResponse> documents, Map<UUID, byte[]> photoBytes, VerifyResponse verify,
                          Instant createdAt) {
}
