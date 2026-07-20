package org.kabieror.elwasys.backend.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stammdaten-Verwaltung für Geräte (Phase 3 AP2, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/DeviceWindow} (Testfälle P10/P11). Löschen ist
 * bewusst OHNE Wächter (1:1 wie {@code Common.Device#delete}/
 * {@code Portal/.../views/DevicesView#deleteDevice}): laufende/vergangene Ausführungen
 * behalten ihren Bezug per {@code ON DELETE SET DEFAULT} (virtuelles Gerät -1, siehe
 * kb/02-data-model.md), Programm-/Benutzergruppen-Zuordnungen fallen per
 * {@code ON DELETE CASCADE} weg.
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public List<DeviceEntity> findAll() {
        return this.deviceRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<DeviceEntity> findById(Integer id) {
        return this.deviceRepository.findById(id);
    }

    /**
     * Entspricht {@code DataManager#getDevicesToDisplay(Location)} - alle Geräte eines
     * Standorts, alphabetisch nach Name. Wird vom Admin-Dashboard (Phase 3 AP3, siehe
     * kb/05-migration-plan.md, {@code DashboardService}) verwendet, um Geräte je Standort zu
     * gruppieren - fachlich identisch zu {@code AdminDashboardView#buildDeviceInfo} im
     * Alt-Portal.
     */
    @Transactional(readOnly = true)
    public List<DeviceEntity> findByLocation(LocationEntity location) {
        return this.deviceRepository.findByLocation_IdOrderByName(location.getId());
    }

    @Transactional
    public DeviceEntity create(String name, int position, LocationEntity location, String fhemName,
            String fhemSwitchName, String fhemPowerName, String deconzUuid, float autoEndPowerThreshold,
            Duration autoEndWaitTime, boolean enabled, Set<ProgramEntity> programs,
            Set<UserGroupEntity> validUserGroups) {
        DeviceEntity device = new DeviceEntity(name, position, location);
        applyFields(device, fhemName, fhemSwitchName, fhemPowerName, deconzUuid, autoEndPowerThreshold,
                autoEndWaitTime, enabled, programs, validUserGroups);
        return this.deviceRepository.save(device);
    }

    @Transactional
    public DeviceEntity update(DeviceEntity device, String name, int position, LocationEntity location,
            String fhemName, String fhemSwitchName, String fhemPowerName, String deconzUuid,
            float autoEndPowerThreshold, Duration autoEndWaitTime, boolean enabled, Set<ProgramEntity> programs,
            Set<UserGroupEntity> validUserGroups) {
        device.setName(name);
        device.setPosition(position);
        device.setLocation(location);
        applyFields(device, fhemName, fhemSwitchName, fhemPowerName, deconzUuid, autoEndPowerThreshold,
                autoEndWaitTime, enabled, programs, validUserGroups);
        return this.deviceRepository.save(device);
    }

    private void applyFields(DeviceEntity device, String fhemName, String fhemSwitchName, String fhemPowerName,
            String deconzUuid, float autoEndPowerThreshold, Duration autoEndWaitTime, boolean enabled,
            Set<ProgramEntity> programs, Set<UserGroupEntity> validUserGroups) {
        device.setFhemName(fhemName);
        device.setFhemSwitchName(fhemSwitchName);
        device.setFhemPowerName(fhemPowerName);
        device.setDeconzUuid(deconzUuid);
        device.setAutoEndPowerThreashold(autoEndPowerThreshold);
        device.setAutoEndWaitTimeSeconds((int) autoEndWaitTime.getSeconds());
        device.setEnabled(enabled);
        device.getPrograms().clear();
        device.getPrograms().addAll(programs);
        device.getValidUserGroups().clear();
        device.getValidUserGroups().addAll(validUserGroups);
    }

    @Transactional
    public void delete(DeviceEntity device) {
        this.deviceRepository.delete(device);
    }
}
