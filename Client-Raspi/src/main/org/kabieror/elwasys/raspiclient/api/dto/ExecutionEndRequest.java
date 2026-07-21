package org.kabieror.elwasys.raspiclient.api.dto;

import java.time.LocalDateTime;

/**
 * Gegenstück zu {@code backend.api.dto.ExecutionEndRequest} (Phase 4 AP4).
 */
public record ExecutionEndRequest(LocalDateTime clientTimestamp) {
}
