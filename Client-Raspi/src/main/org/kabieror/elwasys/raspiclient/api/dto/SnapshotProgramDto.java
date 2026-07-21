package org.kabieror.elwasys.raspiclient.api.dto;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.kabieror.elwasys.common.ProgramType;

/**
 * Gegenstück zu {@code backend.api.dto.SnapshotProgramDto} (Phase 4 AP6, siehe
 * kb/05-migration-plan.md). {@code validUserGroupIds} referenziert
 * {@link SnapshotUserGroupDto#id()}. Preisfelder (Flagfall/Rate/Zeiteinheit) entsprechen 1:1
 * {@link ProgramDto}, damit {@code offline.OfflinePricing} dieselbe Rechnung wie das Backend
 * ({@code PricingService}) nachvollziehen kann.
 */
public record SnapshotProgramDto(Integer id, String name, ProgramType type, int maxDurationSeconds,
        int freeDurationSeconds, BigDecimal flagfall, BigDecimal rate, ChronoUnit timeUnit, boolean autoEnd,
        int earliestAutoEndSeconds, boolean enabled, List<Integer> validUserGroupIds) {
}
