package org.kabieror.elwasys.backend.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;

public record ExecutionDto(Integer id, Integer deviceId, Integer programId, Integer userId, LocalDateTime start,
        LocalDateTime stop, boolean finished, BigDecimal price) {

    public static ExecutionDto of(ExecutionEntity execution, BigDecimal price) {
        return new ExecutionDto(execution.getId(), execution.getDevice().getId(), execution.getProgram().getId(),
                execution.getUser().getId(), execution.getStart(), execution.getStop(), execution.isFinished(),
                price);
    }
}
