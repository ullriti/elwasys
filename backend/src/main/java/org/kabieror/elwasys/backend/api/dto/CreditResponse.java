package org.kabieror.elwasys.backend.api.dto;

import java.math.BigDecimal;

public record CreditResponse(Integer userId, BigDecimal credit) {
}
