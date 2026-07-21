package org.kabieror.elwasys.raspiclient.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Gegenstück zu {@code backend.api.dto.ExecutionDto} (Phase 4 AP4).
 */
public record ExecutionDto(Integer id, Integer deviceId, Integer programId, Integer userId, LocalDateTime start,
        LocalDateTime stop, boolean finished, BigDecimal price) {
}
