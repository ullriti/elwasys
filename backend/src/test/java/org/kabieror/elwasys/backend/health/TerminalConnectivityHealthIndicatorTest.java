package org.kabieror.elwasys.backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.ws.TerminalConnectionRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Deterministischer Unit-Test (Mockito, keine DB) für {@link TerminalConnectivityHealthIndicator}
 * (Issue #32 - Betriebskonzept Dauerbetrieb): ein aktiver Standort ohne verbundenes Terminal
 * zieht den Status auf {@link Status#OUT_OF_SERVICE}, ein Standort ohne Geräte bleibt außen vor.
 */
class TerminalConnectivityHealthIndicatorTest {

    private final LocationService locationService = mock(LocationService.class);
    private final DeviceService deviceService = mock(DeviceService.class);
    private final TerminalConnectionRegistry connectionRegistry = mock(TerminalConnectionRegistry.class);

    private final TerminalConnectivityHealthIndicator indicator =
            new TerminalConnectivityHealthIndicator(this.locationService, this.deviceService, this.connectionRegistry);

    private final List<DeviceEntity> devices = new java.util.ArrayList<>();

    private LocationEntity location(int id, String name, boolean hasDevice) {
        LocationEntity location = mock(LocationEntity.class);
        when(location.getId()).thenReturn(id);
        when(location.getName()).thenReturn(name);
        if (hasDevice) {
            DeviceEntity device = mock(DeviceEntity.class);
            when(device.getLocation()).thenReturn(location);
            this.devices.add(device);
        }
        // Alle in diesem Test angelegten Geräte stehen dem Indicator über findAll() zur Verfügung.
        when(this.deviceService.findAll()).thenReturn(this.devices);
        return location;
    }

    @Test
    void allActiveLocationsConnectedIsUp() {
        LocationEntity a = location(1, "Keller A", true);
        LocationEntity b = location(2, "Keller B", true);
        when(this.locationService.findAll()).thenReturn(List.of(a, b));
        when(this.connectionRegistry.isConnected(1)).thenReturn(true);
        when(this.connectionRegistry.isConnected(2)).thenReturn(true);

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeLocations", 2).containsEntry("connectedLocations", 2);
        assertThat(health.getDetails()).doesNotContainKey("disconnectedLocations");
    }

    @Test
    void activeLocationWithoutConnectionIsOutOfService() {
        LocationEntity connected = location(1, "Keller A", true);
        LocationEntity missing = location(2, "Keller B", true);
        when(this.locationService.findAll()).thenReturn(List.of(connected, missing));
        when(this.connectionRegistry.isConnected(1)).thenReturn(true);
        when(this.connectionRegistry.isConnected(2)).thenReturn(false);

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("activeLocations", 2).containsEntry("connectedLocations", 1);
        assertThat(health.getDetails().get("disconnectedLocations")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(String.class)).containsExactly("Keller B");
    }

    @Test
    void locationWithoutDevicesIsIgnored() {
        // Standort ohne Gerät ist betrieblich nicht in Benutzung -> darf trotz fehlender
        // Verbindung KEIN OUT_OF_SERVICE auslösen (Fehlalarm-Vermeidung).
        LocationEntity empty = location(1, "Leerer Standort", false);
        when(this.locationService.findAll()).thenReturn(List.of(empty));

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("activeLocations", 0).containsEntry("connectedLocations", 0);
    }
}
