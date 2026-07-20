package org.kabieror.elwasys.backend.api.dto;

import java.math.BigDecimal;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;

public record ProgramDto(Integer id, String name, ProgramType type, int maxDurationSeconds, int freeDurationSeconds,
        BigDecimal flagfall, BigDecimal rate, TimeUnitType timeUnit, boolean autoEnd, int earliestAutoEndSeconds,
        boolean enabled, BigDecimal priceAtMaxDuration) {

    public static ProgramDto of(ProgramEntity program, BigDecimal priceAtMaxDuration) {
        return new ProgramDto(program.getId(), program.getName(), program.getType(), program.getMaxDurationSeconds(),
                program.getFreeDurationSeconds(), program.getFlagfall(), program.getRate(), program.getTimeUnit(),
                program.isAutoEnd(), program.getEarliestAutoEndSeconds(), program.isEnabled(), priceAtMaxDuration);
    }
}
