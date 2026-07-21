package org.kabieror.elwasys.raspiclient.api.dto;

import java.math.BigDecimal;

/**
 * Gegenstück zu {@code backend.api.dto.CreditResponse} (Phase 4 AP4).
 */
public record CreditResponse(Integer userId, BigDecimal credit) {
}
