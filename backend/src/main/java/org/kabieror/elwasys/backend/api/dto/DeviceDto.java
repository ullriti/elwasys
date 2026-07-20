package org.kabieror.elwasys.backend.api.dto;

import java.util.List;
import org.kabieror.elwasys.backend.domain.DeviceEntity;

/**
 * Geräteliste für den Standort des Terminal-Tokens (AP4). {@code programs} ist bereits
 * benutzerbezogen gefiltert (siehe
 * {@link org.kabieror.elwasys.backend.service.PermissionService#getAvailablePrograms}) -
 * genau die Programme, die {@code GET /api/v1/devices?userId=...} für DIESEN Benutzer an
 * diesem Gerät anbieten würde.
 */
public record DeviceDto(Integer id, String name, int position, boolean enabled, boolean usableByUser,
        boolean occupied, List<ProgramDto> programs) {

    public static DeviceDto of(DeviceEntity device, boolean usableByUser, boolean occupied,
            List<ProgramDto> programs) {
        return new DeviceDto(device.getId(), device.getName(), device.getPosition(), device.isEnabled(),
                usableByUser, occupied, programs);
    }
}
