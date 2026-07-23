package org.kabieror.elwasys.backend.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.events.DeviceChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stammdaten-Verwaltung für Geräte (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/DeviceWindow} (Testfälle P10/P11). Vergangene
 * (abgeschlossene) Ausführungen behalten beim Löschen ihren Bezug per
 * {@code ON DELETE SET DEFAULT} (virtuelles Gerät -1, siehe docs/kb/02-data-model.md),
 * Programm-/Benutzergruppen-Zuordnungen fallen per {@code ON DELETE CASCADE} weg.
 *
 * <p>Issue #49 (Pre-Launch AP5): Anders als bisher wird ein Gerät mit einer noch NICHT
 * abgeschlossenen (laufenden/abgelaufenen) Ausführung NICHT mehr gelöscht - konsistent mit den
 * Wächtern für Standort/Programm/Benutzergruppe. Sonst fiele die laufende Ausführung per
 * {@code ON DELETE SET DEFAULT} auf das Sentinel-Gerät -1 und belastete das Guthaben des
 * Nutzers weiter, bis ein Admin sie manuell abräumt.
 */
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DeviceService(DeviceRepository deviceRepository, ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.executionRepository = executionRepository;
        this.eventPublisher = eventPublisher;
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
     * docs/kb/05-migration-plan.md, {@code DashboardService}) verwendet, um Geräte je Standort zu
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
        device = this.deviceRepository.save(device);
        this.eventPublisher.publishEvent(new DeviceChangedEvent(device.getId()));
        return device;
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
        device = this.deviceRepository.save(device);
        this.eventPublisher.publishEvent(new DeviceChangedEvent(device.getId()));
        return device;
    }

    private void applyFields(DeviceEntity device, String fhemName, String fhemSwitchName, String fhemPowerName,
            String deconzUuid, float autoEndPowerThreshold, Duration autoEndWaitTime, boolean enabled,
            Set<ProgramEntity> programs, Set<UserGroupEntity> validUserGroups) {
        device.setFhemName(fhemName);
        device.setFhemSwitchName(fhemSwitchName);
        device.setFhemPowerName(fhemPowerName);
        device.setDeconzUuid(deconzUuid);
        device.setAutoEndPowerThreshold(autoEndPowerThreshold);
        device.setAutoEndWaitTimeSeconds((int) autoEndWaitTime.getSeconds());
        device.setEnabled(enabled);
        device.getPrograms().clear();
        device.getPrograms().addAll(programs);
        device.getValidUserGroups().clear();
        device.getValidUserGroups().addAll(validUserGroups);
    }

    /**
     * Löscht ein Gerät.
     *
     * <p>Der Wächter greift bei einer GESTARTETEN, nicht abgeschlossenen Ausführung
     * ({@code start IS NOT NULL AND finished = false}) - dem laufenden/abgelaufenen Fall des
     * Issue-#49-Scopes. Eine nur angelegte, noch nie gestartete Ausführung ({@code start IS NULL})
     * ist bewusst nicht abgedeckt (sie entsteht praktisch nur transient im Start-Pfad).
     *
     * @throws EntityInUseException wenn das Gerät noch eine nicht abgeschlossene
     *                              (laufende/abgelaufene) Ausführung trägt (Issue #49)
     */
    @Transactional
    public void delete(DeviceEntity device) {
        Integer deviceId = device.getId();
        if (this.executionRepository.existsByDevice_IdAndFinishedFalseAndStartIsNotNull(deviceId)) {
            throw new EntityInUseException("Das Gerät " + device.getName()
                    + " hat noch eine laufende Ausführung und kann nicht gelöscht werden.");
        }
        this.deviceRepository.delete(device);
        this.eventPublisher.publishEvent(new DeviceChangedEvent(deviceId));
    }
}
