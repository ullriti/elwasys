package org.kabieror.elwasys.raspiclient.api.dto;

import java.util.List;

/**
 * Gegenstück zu {@code backend.api.dto.SnapshotDeviceDto} (Phase 4 AP6, siehe
 * kb/05-migration-plan.md). {@code validUserGroupIds}/{@code programIds} referenzieren
 * {@link SnapshotUserGroupDto#id()}/{@link SnapshotProgramDto#id()} aus demselben
 * {@link SnapshotDto} - genug, um die Berechtigungsprüfung offline gruppenbasiert
 * nachzubilden (siehe {@code offline.OfflineGateway}).
 */
public record SnapshotDeviceDto(Integer id, String name, int position, boolean enabled,
        List<Integer> validUserGroupIds, List<Integer> programIds, String fhemName, String fhemSwitchName,
        String fhemPowerName, String deconzUuid, float autoEndPowerThreashold, int autoEndWaitTimeSeconds) {
}
