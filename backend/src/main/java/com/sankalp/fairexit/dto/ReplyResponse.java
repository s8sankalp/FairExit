package com.sankalp.fairexit.dto;

import java.util.UUID;

/**
 * Two drafts for the tenant to copy. The disclaimer is shown to the tenant;
 * it is not part of the message sent to the landlord.
 */
public record ReplyResponse(UUID auditId, String polite, String firm, String disclaimer) {
}
