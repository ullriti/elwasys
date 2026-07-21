package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link UserGroupService} (Phase 3 AP2, siehe kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/UserGroupWindow} (Testfälle
 * P9/P13).
 */
class UserGroupServiceTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserGroupService userGroupService;

    private UserGroupEntity group(String name) {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique(name), DiscountType.NONE, 0));
    }

    @Test
    void createsAndUpdatesAGroup() {
        UserGroupEntity created = this.userGroupService.create(Fixtures.unique("Waschküche"), DiscountType.FACTOR,
                0.1);
        assertThat(created.getId()).isNotNull();

        UserGroupEntity updated = this.userGroupService.update(created, Fixtures.unique("Renamed"), DiscountType.FIX,
                0.5);
        assertThat(updated.getDiscountType()).isEqualTo(DiscountType.FIX);
        assertThat(updated.getDiscountValue()).isEqualTo(0.5);
    }

    @Test
    void deletingAGroupReassignsItsUsersToAnotherGroup() {
        UserGroupEntity toDelete = group("toDelete");
        UserGroupEntity other = group("other");
        UserEntity user = this.userRepository.save(
                new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), toDelete));

        this.userGroupService.delete(toDelete);

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getGroup()).as("P13: Benutzer der gelöschten Gruppe bekommen eine andere Gruppe")
                .isNotEqualTo(toDelete);
        assertThat(this.userGroupRepository.findById(toDelete.getId())).isEmpty();
    }

    // Der Fall "es gibt gar keine andere Gruppe mehr" lässt sich in dieser gemeinsam
    // genutzten Testdatenbank nicht sauber herstellen (andere Testklassen legen parallel
    // eigene Gruppen an/lassen sie stehen, siehe AbstractBackendIT-Javadoc: Testdaten werden
    // committet, nicht zurückgerollt) - dieser Zweig ist stattdessen als reiner
    // Mockito-Unit-Test abgedeckt, siehe UserGroupServiceDeleteGuardTest.

    @Test
    void setValidLocationsTogglesGroupMembershipFromTheLocationSide() {
        UserGroupEntity group = group("locGroup");
        LocationEntity included = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc-in")));
        LocationEntity excluded = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc-out")));

        this.userGroupService.setValidLocations(group, Set.of(included.getId()));

        assertThat(this.userGroupService.findValidLocations(group)).containsExactly(included);
        assertThat(this.locationRepository.findById(excluded.getId()).orElseThrow().getValidUserGroups()).doesNotContain(
                group);

        // Entfernen der Freigabe funktioniert ebenso (leere Zielmenge).
        this.userGroupService.setValidLocations(group, Set.of());
        assertThat(this.userGroupService.findValidLocations(group)).isEmpty();
    }

    @Test
    void setValidDevicesAndProgramsToggleGroupMembership() {
        UserGroupEntity group = group("devProgGroup");
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
        ProgramEntity program = this.programRepository.save(
                new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 60));

        this.userGroupService.setValidDevices(group, Set.of(device.getId()));
        this.userGroupService.setValidPrograms(group, Set.of(program.getId()));

        assertThat(this.userGroupService.findValidDevices(group)).containsExactly(device);
        assertThat(this.userGroupService.findValidPrograms(group)).containsExactly(program);
    }
}
