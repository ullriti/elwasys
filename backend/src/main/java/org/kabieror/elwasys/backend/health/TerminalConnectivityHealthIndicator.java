package org.kabieror.elwasys.backend.health;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.ws.TerminalConnectionRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * Betrieblicher Health-Indicator (Issue #32 - Betriebskonzept Dauerbetrieb, Alerting-Grundlage):
 * meldet, wenn ein betrieblich genutzter Standort aktuell KEIN per WebSocket verbundenes
 * Terminal hat. Ein Standort ohne Terminal-Verbindung nimmt keine Buchungen mehr an und wäre
 * sonst nur durch Nutzerbeschwerden sichtbar (stilles Fehlerbild).
 *
 * <p>„Aktiver Standort" = ein Standort mit mindestens einem zugeordneten Gerät. Es gibt kein
 * eigenes „aktiv"-Flag am Standort (siehe {@link LocationEntity}); ein Standort ohne Gerät ist
 * betrieblich nicht in Benutzung (z.B. frisch angelegt oder im Abbau) und wird daher bewusst
 * NICHT als fehlend gewertet, um Fehlalarme zu vermeiden.
 *
 * <p>Bei mindestens einem aktiven Standort ohne Verbindung ist der Status
 * {@link Status#OUT_OF_SERVICE} (der Backend-Prozess selbst ist gesund, aber ein Terminal
 * fehlt - operative Aufmerksamkeit nötig). Details (Standortnamen) enthalten keine Geheimnisse
 * und sind über den Actuator nur {@code when-authorized} sichtbar (siehe application.yml).
 */
@Component
public class TerminalConnectivityHealthIndicator implements HealthIndicator {

    private final LocationService locationService;

    private final DeviceService deviceService;

    private final TerminalConnectionRegistry connectionRegistry;

    public TerminalConnectivityHealthIndicator(LocationService locationService, DeviceService deviceService,
            TerminalConnectionRegistry connectionRegistry) {
        this.locationService = locationService;
        this.deviceService = deviceService;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public Health health() {
        // Geräte einmal laden und die Standort-IDs mit mindestens einem Gerät sammeln (statt je
        // Standort eine eigene Abfrage) - /actuator/health kann von externem Alerting eng gepollt
        // werden, daher keine N+1-Abfragen pro Aufruf.
        Set<Integer> locationsWithDevices = new HashSet<>();
        for (DeviceEntity device : this.deviceService.findAll()) {
            if (device.getLocation() != null) {
                locationsWithDevices.add(device.getLocation().getId());
            }
        }

        int activeLocations = 0;
        List<String> disconnected = new ArrayList<>();
        for (LocationEntity location : this.locationService.findAll()) {
            // Nur betrieblich genutzte Standorte (mind. ein Gerät) erwarten eine Terminal-Verbindung.
            if (!locationsWithDevices.contains(location.getId())) {
                continue;
            }
            activeLocations++;
            if (!this.connectionRegistry.isConnected(location.getId())) {
                disconnected.add(location.getName());
            }
        }

        Health.Builder builder = disconnected.isEmpty() ? Health.up() : Health.status(Status.OUT_OF_SERVICE);
        builder.withDetail("activeLocations", activeLocations);
        builder.withDetail("connectedLocations", activeLocations - disconnected.size());
        if (!disconnected.isEmpty()) {
            builder.withDetail("disconnectedLocations", disconnected);
        }
        return builder.build();
    }
}
