package org.kabieror.elwasys.backend.api.dto;

import java.util.List;
import org.kabieror.elwasys.backend.domain.DeviceEntity;

/**
 * Geräteliste für den Standort des Terminal-Tokens (AP4). {@code programs} ist bereits
 * benutzerbezogen gefiltert (siehe
 * {@link org.kabieror.elwasys.backend.service.PermissionService#getAvailablePrograms}) -
 * genau die Programme, die {@code GET /api/v1/devices?userId=...} für DIESEN Benutzer an
 * diesem Gerät anbieten würde.
 *
 * <p><b>Gateway-/Hardwarefelder (AP3, Phase 4, additiv ergänzt):</b> {@code fhemName}/
 * {@code fhemSwitchName}/{@code fhemPowerName}/{@code deconzUuid}/
 * {@code autoEndPowerThreshold}/{@code autoEndWaitTimeSeconds} entsprechen 1:1 den
 * gleichnamigen {@link DeviceEntity}-Feldern (siehe kb/02-data-model.md) - der Alt-Client
 * lädt sie bislang über {@code DataManager#getDevicesToDisplay}/{@code Device}, das
 * Terminal-Cutover (AP4) braucht sie über die API, um die Steckdose des jeweiligen
 * Gateways (fhem ODER deCONZ) korrekt anzusteuern und automatische Programmenden per
 * Leistungsmessung auszulösen.
 */
public record DeviceDto(Integer id, String name, int position, boolean enabled, boolean usableByUser,
        boolean occupied, List<ProgramDto> programs, String fhemName, String fhemSwitchName, String fhemPowerName,
        String deconzUuid, float autoEndPowerThreshold, int autoEndWaitTimeSeconds) {

    public static DeviceDto of(DeviceEntity device, boolean usableByUser, boolean occupied,
            List<ProgramDto> programs) {
        return new DeviceDto(device.getId(), device.getName(), device.getPosition(), device.isEnabled(),
                usableByUser, occupied, programs, device.getFhemName(), device.getFhemSwitchName(),
                device.getFhemPowerName(), device.getDeconzUuid(), device.getAutoEndPowerThreshold(),
                device.getAutoEndWaitTimeSeconds());
    }
}
