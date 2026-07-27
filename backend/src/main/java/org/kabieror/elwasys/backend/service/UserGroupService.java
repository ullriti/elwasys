package org.kabieror.elwasys.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.events.UserChangedEvent;
import org.kabieror.elwasys.backend.events.UserGroupChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stammdaten-Verwaltung für Benutzergruppen (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/UserGroupWindow} (Testfall P9).
 *
 * <p>Anders als {@link DeviceEntity}/{@link ProgramEntity}/{@link LocationEntity} besitzt
 * {@link UserGroupEntity} selbst KEINE Sammlung ihrer zugelassenen Standorte/Geräte/
 * Programme - die drei {@code @ManyToMany}-Relationen sind unidirektional von der jeweils
 * anderen Seite aus modelliert (siehe deren Klassenkommentare). {@code setValidLocations}/
 * {@code setValidDevices}/{@code setValidPrograms} bilden daher die "Gruppen-Perspektive"
 * des Alt-Fensters ({@code UserGroupWindow}s drei {@code TwinColSelect}s) nach, indem sie
 * jede betroffene Entität einzeln anfassen und die Gruppenzugehörigkeit dort umschalten -
 * fachlich identisch zu {@code Common.UserGroup#setValidLocations}/{@code #setValidDevices}/
 * {@code #setValidPrograms}, nur von der jeweils anderen Tabellenseite aus.
 */
@Service
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final DeviceRepository deviceRepository;
    private final ProgramRepository programRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserGroupService(UserGroupRepository userGroupRepository, UserRepository userRepository,
            LocationRepository locationRepository, DeviceRepository deviceRepository,
            ProgramRepository programRepository, ApplicationEventPublisher eventPublisher) {
        this.userGroupRepository = userGroupRepository;
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
        this.programRepository = programRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<UserGroupEntity> findAll() {
        return this.userGroupRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<UserGroupEntity> findById(Integer id) {
        return this.userGroupRepository.findById(id);
    }

    @Transactional
    public UserGroupEntity create(String name, DiscountType discountType, double discountValue) {
        UserGroupEntity group = this.userGroupRepository.save(new UserGroupEntity(name, discountType, discountValue));
        this.eventPublisher.publishEvent(new UserGroupChangedEvent(group.getId()));
        return group;
    }

    @Transactional
    public UserGroupEntity update(UserGroupEntity group, String name, DiscountType discountType,
            double discountValue) {
        group.setName(name);
        group.setDiscountType(discountType);
        group.setDiscountValue(discountValue);
        group = this.userGroupRepository.save(group);
        this.eventPublisher.publishEvent(new UserGroupChangedEvent(group.getId()));
        return group;
    }

    /**
     * Löscht eine Benutzergruppe. 1:1-Portierung von {@code Common.UserGroup#delete}: alle
     * Benutzer dieser Gruppe werden zunächst einer anderen (beliebigen) Gruppe zugewiesen
     * (das Alt-Code-SQL {@code SELECT id FROM user_groups WHERE id<>? LIMIT 1} - siehe
     * {@link org.kabieror.elwasys.backend.repository.UserGroupRepository#findFirstByIdNotOrderByIdAsc}),
     * dann wird die Gruppe gelöscht (die n:m-Zuordnungen zu Standorten/Geräten/Programmen
     * fallen per {@code ON DELETE CASCADE} weg, siehe docs/kb/02-data-model.md).
     *
     * @throws EntityInUseException wenn es keine andere Benutzergruppe gibt, der die
     *                              Benutzer zugewiesen werden könnten (im Alt-Code hätte das
     *                              zu einem NOT-NULL-Constraint-Verstoß geführt - hier
     *                              stattdessen ein sprechender, vorab geprüfter Fehler)
     */
    @Transactional
    public void delete(UserGroupEntity group) {
        UserGroupEntity fallbackGroup = this.userGroupRepository.findFirstByIdNotOrderByIdAsc(group.getId())
                .orElseThrow(() -> new EntityInUseException(
                        "Die letzte verbleibende Benutzergruppe kann nicht gelöscht werden."));

        for (UserEntity user : this.userRepository.findAll()) {
            if (user.getGroup() != null && user.getGroup().equals(group)) {
                user.setGroup(fallbackGroup);
                this.userRepository.save(user);
                this.eventPublisher.publishEvent(new UserChangedEvent(user.getId()));
            }
        }

        Integer groupId = group.getId();
        this.userGroupRepository.delete(group);
        this.eventPublisher.publishEvent(new UserGroupChangedEvent(groupId));
    }

    /**
     * Setzt, welche Standorte für diese Gruppe zugelassen sind - siehe Klassenkommentar für
     * die "von der anderen Tabellenseite aus"-Modellierung. Veröffentlicht bewusst ein
     * {@link UserGroupChangedEvent} (aus der Perspektive des Gruppen-Dialogs, der diese Methode
     * aufruft, ist das Teil des Gruppe-Speicherns) - fachlich ändern sich zwar auch die
     * betroffenen {@link LocationEntity}s, das ist aber keine für die
     * Standort-Stammdaten-Ansicht sichtbare Änderung (die zeigt nur Name/Anzahl Gruppen, siehe
     * {@code AdminLocationsView}, kein Live-relevanter Unterschied).
     */
    @Transactional
    public void setValidLocations(UserGroupEntity group, Set<Integer> locationIds) {
        syncGroupMembership(group, locationIds, this.locationRepository, LocationEntity::getId,
                LocationEntity::getValidUserGroups);
    }

    /**
     * Siehe {@link #setValidLocations} Javadoc für die Begründung, warum hier nur ein
     * {@link UserGroupChangedEvent} statt zusätzlicher {@code DeviceChangedEvent}s pro Gerät
     * veröffentlicht wird.
     */
    @Transactional
    public void setValidDevices(UserGroupEntity group, Set<Integer> deviceIds) {
        syncGroupMembership(group, deviceIds, this.deviceRepository, DeviceEntity::getId,
                DeviceEntity::getValidUserGroups);
    }

    /**
     * Siehe {@link #setValidLocations} Javadoc für die Begründung, warum hier nur ein
     * {@link UserGroupChangedEvent} statt zusätzlicher {@code ProgramChangedEvent}s pro
     * Programm veröffentlicht wird.
     */
    @Transactional
    public void setValidPrograms(UserGroupEntity group, Set<Integer> programIds) {
        syncGroupMembership(group, programIds, this.programRepository, ProgramEntity::getId,
                ProgramEntity::getValidUserGroups);
    }

    /**
     * Gemeinsame Umschalt-Logik der drei {@code setValid*}-Methoden (die sich nur in
     * Repository und Zugriffspfad auf die Gegenseite unterscheiden, siehe Klassenkommentar):
     * jede Entität wird genau dann gespeichert, wenn sich ihre Gruppenzugehörigkeit
     * tatsächlich ändert - unveränderte Zeilen erzeugen wie bisher kein Update. Am Ende genau
     * EIN {@link UserGroupChangedEvent} (Begründung siehe {@link #setValidLocations}).
     *
     * @param targetIds     die Ids der Entitäten, für die die Gruppe künftig zugelassen sein soll
     * @param idAccessor    liest die Id der Entität (Abgleich gegen {@code targetIds})
     * @param groupsAccessor liest die veränderbare Gruppen-Sammlung der Entität
     */
    private <E> void syncGroupMembership(UserGroupEntity group, Set<Integer> targetIds,
            JpaRepository<E, Integer> repository, Function<E, Integer> idAccessor,
            Function<E, Set<UserGroupEntity>> groupsAccessor) {
        for (E entity : repository.findAll()) {
            Set<UserGroupEntity> validGroups = groupsAccessor.apply(entity);
            boolean shouldBeValid = targetIds.contains(idAccessor.apply(entity));
            boolean isValid = validGroups.contains(group);
            if (shouldBeValid && !isValid) {
                validGroups.add(group);
                repository.save(entity);
            } else if (!shouldBeValid && isValid) {
                validGroups.remove(group);
                repository.save(entity);
            }
        }
        this.eventPublisher.publishEvent(new UserGroupChangedEvent(group.getId()));
    }

    @Transactional(readOnly = true)
    public List<LocationEntity> findValidLocations(UserGroupEntity group) {
        return findValidFor(group, this.locationRepository, LocationEntity::getValidUserGroups);
    }

    @Transactional(readOnly = true)
    public List<DeviceEntity> findValidDevices(UserGroupEntity group) {
        return findValidFor(group, this.deviceRepository, DeviceEntity::getValidUserGroups);
    }

    @Transactional(readOnly = true)
    public List<ProgramEntity> findValidPrograms(UserGroupEntity group) {
        return findValidFor(group, this.programRepository, ProgramEntity::getValidUserGroups);
    }

    /** Gegenstück zu {@link #syncGroupMembership} für die drei {@code findValid*}-Methoden. */
    private <E> List<E> findValidFor(UserGroupEntity group, JpaRepository<E, Integer> repository,
            Function<E, Set<UserGroupEntity>> groupsAccessor) {
        return repository.findAll().stream().filter(entity -> groupsAccessor.apply(entity).contains(group)).toList();
    }
}
