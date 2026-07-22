package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link LocationService} (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/LocationWindow} (Testfall P14), erweitert um
 * Anlegen/Löschen für die neue eigenständige Standort-Ansicht (siehe
 * {@code AdminLayout}-Javadoc: "Standorte" ist im Alt-Portal nur über einen Dashboard-Dialog
 * erreichbar).
 */
class LocationServiceTest extends AbstractBackendIT {

    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private LocationService locationService;

    @Test
    void createsAndUpdatesALocation() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));

        LocationEntity created = this.locationService.create(Fixtures.unique("Waschküche"), Set.of(group));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getValidUserGroups()).containsExactly(group);

        LocationEntity updated = this.locationService.update(created, "Neuer Name", Set.of());
        assertThat(updated.getName()).isEqualTo("Neuer Name");
        assertThat(updated.getValidUserGroups()).isEmpty();
    }

    @Test
    void deletingALocationStillUsedByADeviceFails() {
        LocationEntity location = this.locationService.create(Fixtures.unique("loc"), Set.of());
        this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));

        assertThatThrownBy(() -> this.locationService.delete(location)).isInstanceOf(EntityInUseException.class)
                .hasMessageContaining("1 Gerät");

        assertThat(this.locationRepository.findById(location.getId())).isPresent();
    }

    @Test
    void deletingAnUnusedLocationSucceeds() {
        LocationEntity location = this.locationService.create(Fixtures.unique("loc"), Set.of());

        this.locationService.delete(location);

        assertThat(this.locationRepository.findById(location.getId())).isEmpty();
    }
}
