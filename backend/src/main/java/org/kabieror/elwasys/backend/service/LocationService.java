package org.kabieror.elwasys.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stammdaten-Verwaltung für Standorte (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/LocationWindow} (Testfall P14).
 * "Standorte" ist als eigener Menüpunkt/View NEU (im Alt-Portal nur über einen
 * Dashboard-Dialog erreichbar) - siehe {@code AdminLayout}-Javadoc; dieser Service liefert
 * dafür zusätzlich Anlegen/Löschen, die es im Alt-Fenster mangels eigener Ansicht so nicht
 * gab (dort nur "Standort per Geräte-Dialog neu anlegen" bzw. implizites Aufräumen über
 * {@code DataManager#removeUnusedLocations}).
 */
@Service
public class LocationService {

    /**
     * Default-Wert für {@code offline.max-duration} (Phase 4 AP6, Auftraggeber-Vorgabe -
     * siehe docs/kb/05-migration-plan.md). Entspricht dem Spalten-Default der additiven Migration
     * {@code V5__add_offline_max_duration_to_locations.sql}.
     */
    public static final int DEFAULT_OFFLINE_MAX_DURATION_MINUTES = 60;

    private final LocationRepository locationRepository;
    private final DeviceRepository deviceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LocationService(LocationRepository locationRepository, DeviceRepository deviceRepository,
            ApplicationEventPublisher eventPublisher) {
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<LocationEntity> findAll() {
        return this.locationRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<LocationEntity> findById(Integer id) {
        return this.locationRepository.findById(id);
    }

    @Transactional
    public LocationEntity create(String name, Set<UserGroupEntity> validUserGroups) {
        return create(name, validUserGroups, DEFAULT_OFFLINE_MAX_DURATION_MINUTES);
    }

    /**
     * Wie {@link #create(String, Set)}, mit zusätzlich einstellbarer
     * {@code offline.max-duration} (Phase 4 AP6, additiv - siehe docs/kb/05-migration-plan.md
     * "Festlegungen zu den Offline-Detailfragen").
     */
    @Transactional
    public LocationEntity create(String name, Set<UserGroupEntity> validUserGroups, int offlineMaxDurationMinutes) {
        LocationEntity location = new LocationEntity(name);
        location.getValidUserGroups().addAll(validUserGroups);
        location.setOfflineMaxDurationMinutes(offlineMaxDurationMinutes);
        location = this.locationRepository.save(location);
        this.eventPublisher.publishEvent(new LocationChangedEvent(location.getId()));
        return location;
    }

    /**
     * Bearbeitet einen Standort. 1:1-Portierung von {@code Common.Location#modify}
     * (aufgerufen aus {@code LocationWindow#save}).
     */
    @Transactional
    public LocationEntity update(LocationEntity location, String name, Set<UserGroupEntity> validUserGroups) {
        return update(location, name, validUserGroups,
                location.getOfflineMaxDurationMinutes() != null ? location.getOfflineMaxDurationMinutes()
                        : DEFAULT_OFFLINE_MAX_DURATION_MINUTES);
    }

    /**
     * Wie {@link #update(LocationEntity, String, Set)}, mit zusätzlich einstellbarer
     * {@code offline.max-duration} (Phase 4 AP6, additiv).
     */
    @Transactional
    public LocationEntity update(LocationEntity location, String name, Set<UserGroupEntity> validUserGroups,
            int offlineMaxDurationMinutes) {
        location.setName(name);
        location.getValidUserGroups().clear();
        location.getValidUserGroups().addAll(validUserGroups);
        location.setOfflineMaxDurationMinutes(offlineMaxDurationMinutes);
        location = this.locationRepository.save(location);
        this.eventPublisher.publishEvent(new LocationChangedEvent(location.getId()));
        return location;
    }

    /**
     * Löscht einen Standort. Analog zum Lösch-Wächter für Programme (siehe
     * {@link ProgramService#delete}): ein Standort mit noch zugeordneten Geräten wird nicht
     * gelöscht. Entspricht fachlich {@code DataManager#removeUnusedLocations} (dort:
     * automatisches Aufräumen unbenutzter Standorte beim Schließen des Geräte-Fensters,
     * WHERE-Klausel "kein Gerät verweist mehr darauf") - hier als expliziter Admin-Vorgang
     * mit Bestätigungsdialog statt impliziter Hintergrundaktion (siehe
     * {@code AdminLocationsView}, neuer eigener Menüpunkt).
     *
     * @throws EntityInUseException wenn dem Standort noch mindestens ein Gerät zugeordnet ist
     */
    @Transactional
    public void delete(LocationEntity location) {
        List<DeviceEntity> devicesAtLocation = this.deviceRepository.findByLocation_IdOrderByName(location.getId());
        if (!devicesAtLocation.isEmpty()) {
            throw new EntityInUseException(
                    "Der Standort " + location.getName() + " wird noch von " + devicesAtLocation.size()
                            + " Gerät(en) verwendet.");
        }
        Integer locationId = location.getId();
        this.locationRepository.delete(location);
        this.eventPublisher.publishEvent(new LocationChangedEvent(locationId));
    }
}
