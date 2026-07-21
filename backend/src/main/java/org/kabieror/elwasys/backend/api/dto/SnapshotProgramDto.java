package org.kabieror.elwasys.backend.api.dto;

import java.math.BigDecimal;
import java.util.List;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;

/**
 * Teil des {@link SnapshotDto} (AP3, Phase 4). {@code validUserGroupIds} referenziert
 * {@link SnapshotUserGroupDto#id()} - genug, um {@code PermissionService
 * #isProgramAvailableForDeviceAndUser} offline gruppenbasiert nachzubilden. Preisfelder
 * (Flagfall/Rate/Zeiteinheit) entsprechen 1:1 {@link ProgramDto}, damit eine spätere
 * Offline-Preisberechnung dieselbe Rechnung wie {@code PricingService} nachvollziehen kann.
 */
public record SnapshotProgramDto(Integer id, String name, ProgramType type, int maxDurationSeconds,
        int freeDurationSeconds, BigDecimal flagfall, BigDecimal rate, TimeUnitType timeUnit, boolean autoEnd,
        int earliestAutoEndSeconds, boolean enabled, List<Integer> validUserGroupIds) {

    public static SnapshotProgramDto of(ProgramEntity program) {
        List<Integer> groupIds = program.getValidUserGroups().stream().map(UserGroupEntity::getId).toList();
        return new SnapshotProgramDto(program.getId(), program.getName(), program.getType(),
                program.getMaxDurationSeconds(), program.getFreeDurationSeconds(), program.getFlagfall(),
                program.getRate(), program.getTimeUnit(), program.isAutoEnd(), program.getEarliestAutoEndSeconds(),
                program.isEnabled(), groupIds);
    }
}
