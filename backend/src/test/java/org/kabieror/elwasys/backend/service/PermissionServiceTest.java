package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
 * Charakterisierungstests für die Berechtigungs-Matrix (Gruppe/Standort/Gerät/Programm,
 * gesperrte Nutzer, deaktivierte Geräte) - siehe docs/kb/05-migration-plan.md, AP2.
 *
 * <p>Diese Regeln sind im Alt-Code NICHT in einer wiederverwendbaren Common-Methode
 * gekapselt, sondern direkt inline in den JavaFX-UI-Controllern des Terminals
 * (siehe {@link PermissionService} Javadoc für die genauen Fundstellen:
 * {@code ui/medium/MainFormController#onCardDetected} für den Standort-Login,
 * {@code ui/medium/controller/DeviceListEntry#applyUserStyle} für den Gerätezugriff,
 * {@code common.Device#getPrograms(User)} für die Programmauswahl). Ein direkter
 * Alt-vs-Neu-Vergleichstest wie bei Pricing/Credit ist daher nicht sinnvoll möglich (kein
 * aufrufbares Alt-Code-Äquivalent ohne JavaFX-Toolkit); stattdessen sind das hier
 * Charakterisierungstests, deren erwartete Werte direkt aus dem zitierten Alt-Code
 * hergeleitet sind.
 */
class PermissionServiceTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PermissionService permissionService;

    private UserGroupEntity group(String name) {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique(name), DiscountType.NONE, 0));
    }

    private UserEntity user(UserGroupEntity group, boolean blocked) {
        UserEntity u = new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group);
        u.setBlocked(blocked);
        return this.userRepository.save(u);
    }

    @Test
    void userIsAllowedAtLocationOnlyWhenGroupIsListedAndUserIsNotBlocked() {
        UserGroupEntity allowedGroup = group("allowed");
        UserGroupEntity otherGroup = group("other");

        LocationEntity location = new LocationEntity(Fixtures.unique("loc"));
        location.getValidUserGroups().add(allowedGroup);
        location = this.locationRepository.save(location);

        UserEntity allowedUser = user(allowedGroup, false);
        UserEntity wrongGroupUser = user(otherGroup, false);
        UserEntity blockedUser = user(allowedGroup, true);

        assertThat(this.permissionService.isUserAllowedAtLocation(allowedUser, location)).isTrue();
        assertThat(this.permissionService.isUserAllowedAtLocation(wrongGroupUser, location)).as(
                "group not listed at location -> not allowed").isFalse();
        assertThat(this.permissionService.isUserAllowedAtLocation(blockedUser, location)).as(
                "blocked user -> not allowed even with valid group").isFalse();
    }

    @Test
    void deviceIsUsableOnlyWhenEnabledAndGroupIsListed() {
        UserGroupEntity allowedGroup = group("allowed");
        UserGroupEntity otherGroup = group("other");

        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));

        DeviceEntity enabledAllowedDevice = new DeviceEntity(Fixtures.unique("dev"), 1, location);
        enabledAllowedDevice.getValidUserGroups().add(allowedGroup);
        enabledAllowedDevice = this.deviceRepository.save(enabledAllowedDevice);

        DeviceEntity disabledDevice = new DeviceEntity(Fixtures.unique("dev"), 2, location);
        disabledDevice.getValidUserGroups().add(allowedGroup);
        disabledDevice.setEnabled(false);
        disabledDevice = this.deviceRepository.save(disabledDevice);

        UserEntity allowedUser = user(allowedGroup, false);
        UserEntity wrongGroupUser = user(otherGroup, false);

        assertThat(this.permissionService.isDeviceUsableByUser(enabledAllowedDevice, allowedUser)).isTrue();
        assertThat(this.permissionService.isDeviceUsableByUser(enabledAllowedDevice, wrongGroupUser)).as(
                "group not listed at device -> not usable").isFalse();
        assertThat(this.permissionService.isDeviceUsableByUser(disabledDevice, allowedUser)).as(
                "disabled device -> not usable even with valid group").isFalse();
    }

    @Test
    void availableProgramsAreFilteredByDeviceAssignmentAndUserGroupButNotByProgramEnabledFlag() {
        // Beobachtung (siehe docs/kb/05-migration-plan.md, PermissionService-Javadoc): der
        // Alt-Code (Device#getPrograms(User)) filtert NICHT zusätzlich auf
        // program.isEnabled() - ein deaktiviertes, aber dem Gerät zugeordnetes und für die
        // Gruppe freigegebenes Programm bleibt wählbar. Wird hier bewusst nachgebildet.
        UserGroupEntity allowedGroup = group("allowed");
        UserGroupEntity otherGroup = group("other");
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));

        ProgramEntity assignedAllowedProgram = this.programRepository.save(
                withValidGroup(new ProgramEntity(Fixtures.unique("p1"), ProgramType.FIXED, 60), allowedGroup));
        ProgramEntity assignedWrongGroupProgram = this.programRepository.save(
                withValidGroup(new ProgramEntity(Fixtures.unique("p2"), ProgramType.FIXED, 60), otherGroup));
        ProgramEntity notAssignedProgram = this.programRepository.save(
                withValidGroup(new ProgramEntity(Fixtures.unique("p3"), ProgramType.FIXED, 60), allowedGroup));
        ProgramEntity disabledButAssignedProgram = new ProgramEntity(Fixtures.unique("p4"), ProgramType.FIXED, 60);
        disabledButAssignedProgram.getValidUserGroups().add(allowedGroup);
        disabledButAssignedProgram.setEnabled(false);
        disabledButAssignedProgram = this.programRepository.save(disabledButAssignedProgram);

        DeviceEntity device = new DeviceEntity(Fixtures.unique("dev"), 1, location);
        device.getPrograms().add(assignedAllowedProgram);
        device.getPrograms().add(assignedWrongGroupProgram);
        device.getPrograms().add(disabledButAssignedProgram);
        // notAssignedProgram bleibt bewusst NICHT dem Gerät zugeordnet.
        device = this.deviceRepository.save(device);

        UserEntity allowedUser = user(allowedGroup, false);

        List<ProgramEntity> available = this.permissionService.getAvailablePrograms(device, allowedUser);

        assertThat(available).containsExactlyInAnyOrder(assignedAllowedProgram, disabledButAssignedProgram);
        assertThat(available).doesNotContain(assignedWrongGroupProgram, notAssignedProgram);

        assertThat(
                this.permissionService.isProgramAvailableForDeviceAndUser(device, assignedAllowedProgram,
                        allowedUser)).isTrue();
        assertThat(
                this.permissionService.isProgramAvailableForDeviceAndUser(device, notAssignedProgram, allowedUser))
                .as("program not assigned to device -> not available").isFalse();
    }

    private ProgramEntity withValidGroup(ProgramEntity program, UserGroupEntity group) {
        program.getValidUserGroups().add(group);
        return program;
    }
}
