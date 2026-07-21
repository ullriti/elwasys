package org.kabieror.elwasys.raspiclient.api.dto;

import java.util.List;

/**
 * Gegenstück zu {@code backend.api.dto.DeviceDto} (Phase 4 AP4): benutzerbezogene
 * Geräteliste ({@code GET /api/v1/devices?userId=...}), {@code programs} bereits
 * gruppengefiltert.
 */
public record DeviceDto(Integer id, String name, int position, boolean enabled, boolean usableByUser,
        boolean occupied, List<ProgramDto> programs, String fhemName, String fhemSwitchName, String fhemPowerName,
        String deconzUuid, float autoEndPowerThreashold, int autoEndWaitTimeSeconds) {
}
