package org.kabieror.elwasys.raspiclient.api.dto;

import java.util.List;

/**
 * Gegenstück zu {@code backend.api.dto.DeviceOverviewDto} (Phase 4 AP3/AP4): anonyme
 * Geräteübersicht ({@code GET /api/v1/devices/overview}) vor dem Kartenlogin, inkl.
 * ungefilterter, ungerabattet bepreister {@code programs} (Phase 4 AP4, additive
 * Backend-Erweiterung - siehe kb/05-migration-plan.md Änderungslog "Phase 4 AP4" für die
 * Begründung: {@code ui/small} zeigt Programme/Preise bereits vor dem Kartenlogin).
 */
public record DeviceOverviewDto(Integer id, String name, int position, boolean enabled, boolean occupied,
        Integer runningExecutionId, Integer lastUserId, String lastUserName, String fhemName, String fhemSwitchName,
        String fhemPowerName, String deconzUuid, float autoEndPowerThreshold, int autoEndWaitTimeSeconds,
        List<ProgramDto> programs) {
}
