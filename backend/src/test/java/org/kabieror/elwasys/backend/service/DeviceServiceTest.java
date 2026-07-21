package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link DeviceService} (Phase 3 AP2, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/DeviceWindow} (Testfälle P10/P11).
 */
class DeviceServiceTest extends AbstractBackendIT {

    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private DeviceService deviceService;

    @Test
    void createsAndUpdatesADevice() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        ProgramEntity program = this.programRepository.save(
                new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 60));
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));

        DeviceEntity created = this.deviceService.create(Fixtures.unique("Waschmaschine"), 1, location, "fhemDev",
                "fhemSwitch", "fhemPower", "", 2.5f, Duration.ofSeconds(30), true, Set.of(program), Set.of(group));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getFhemName()).isEqualTo("fhemDev");
        assertThat(created.getPrograms()).containsExactly(program);
        assertThat(created.getValidUserGroups()).containsExactly(group);

        LocationEntity otherLocation = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc2")));
        DeviceEntity updated = this.deviceService.update(created, "Neuer Name", 2, otherLocation, "fhemDev2",
                "fhemSwitch2", "fhemPower2", "deconz-uuid", 5f, Duration.ofSeconds(60), false, Set.of(), Set.of());

        assertThat(updated.getName()).isEqualTo("Neuer Name");
        assertThat(updated.getLocation()).isEqualTo(otherLocation);
        assertThat(updated.isEnabled()).as("P11: Gerät deaktivieren").isFalse();
        assertThat(updated.getPrograms()).isEmpty();
        assertThat(updated.getValidUserGroups()).isEmpty();
        assertThat(updated.getDeconzUuid()).isEqualTo("deconz-uuid");
    }

    @Test
    void findByLocationReturnsOnlyDevicesOfThatLocationOrderedByName() {
        LocationEntity locationA = this.locationRepository.save(new LocationEntity(Fixtures.unique("locA")));
        LocationEntity locationB = this.locationRepository.save(new LocationEntity(Fixtures.unique("locB")));
        DeviceEntity deviceZ = this.deviceService.create("Z-" + Fixtures.unique("dev"), 1, locationA, "", "", "", "",
                1f, Duration.ofSeconds(10), true, Set.of(), Set.of());
        DeviceEntity deviceA = this.deviceService.create("A-" + Fixtures.unique("dev"), 2, locationA, "", "", "", "",
                1f, Duration.ofSeconds(10), true, Set.of(), Set.of());
        this.deviceService.create(Fixtures.unique("otherLocDev"), 1, locationB, "", "", "", "", 1f,
                Duration.ofSeconds(10), true, Set.of(), Set.of());

        assertThat(this.deviceService.findByLocation(locationA)).containsExactly(deviceA, deviceZ);
    }

    @Test
    void deletesADeviceWithoutAGuard() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceService.create(Fixtures.unique("dev"), 1, location, "", "", "", "", 1f,
                Duration.ofSeconds(10), true, Set.of(), Set.of());

        this.deviceService.delete(device);

        assertThat(this.deviceRepository.findById(device.getId())).isEmpty();
    }
}
