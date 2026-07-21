package org.kabieror.elwasys.backend.api.dto;

import java.util.List;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;

/**
 * Teil des {@link SnapshotDto} (AP3, Phase 4, siehe Konzeptskizze Punkt 1: "Geräte/
 * Programme/Preise, Berechtigungen"). {@code validUserGroupIds}/{@code programIds}
 * referenzieren {@link SnapshotUserGroupDto#id()}/{@link SnapshotProgramDto#id()} aus
 * demselben {@link SnapshotDto} - genug, um {@code PermissionService#isDeviceUsableByUser}
 * offline gruppenbasiert nachzubilden.
 */
public record SnapshotDeviceDto(Integer id, String name, int position, boolean enabled,
        List<Integer> validUserGroupIds, List<Integer> programIds, String fhemName, String fhemSwitchName,
        String fhemPowerName, String deconzUuid, float autoEndPowerThreshold, int autoEndWaitTimeSeconds) {

    public static SnapshotDeviceDto of(DeviceEntity device) {
        List<Integer> groupIds = device.getValidUserGroups().stream().map(UserGroupEntity::getId).toList();
        List<Integer> programIds = device.getPrograms().stream().map(p -> p.getId()).toList();
        return new SnapshotDeviceDto(device.getId(), device.getName(), device.getPosition(), device.isEnabled(),
                groupIds, programIds, device.getFhemName(), device.getFhemSwitchName(), device.getFhemPowerName(),
                device.getDeconzUuid(), device.getAutoEndPowerThreshold(), device.getAutoEndWaitTimeSeconds());
    }
}
