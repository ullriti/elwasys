package org.kabieror.elwasys.backend.health;

import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * Betrieblicher Health-Indicator (Issue #89 - Dead-Letter-Sichtbarkeit): meldet vom Terminal
 * gemeldete, noch nicht quittierte Offline-Vorfälle.
 *
 * <p>Ein dead-gelettert Journal-Eintrag ist eine **verlorene Offline-Buchung (Geld)**, eine
 * nicht kompensierte Geister-Ausführung eine dauerhaft offene Ausführung. Beides war vor
 * Issue #89 nur als {@code logger.error} im lokalen Log des Terminals sichtbar und erreichte
 * damit niemanden - jetzt melden die Terminals solche Vorfälle über den Wartungs-WebSocket,
 * und dieser Indicator hebt sie in den Alerting-Endpunkt {@code /actuator/health/operational}
 * (siehe deploy/monitoring/).
 *
 * <p>Bei mindestens einem offenen Vorfall ist der Status {@link Status#OUT_OF_SERVICE} (der
 * Backend-Prozess ist gesund, es besteht aber operativer Handlungsbedarf). Der Alarm endet
 * durch die **Quittierung im Portal** - bewusst nicht automatisch nach Zeitablauf: ein
 * Geldverlust soll aktiv zur Kenntnis genommen werden, statt still zu verfallen. Das Detail
 * {@code openCount} nennt nur die Anzahl (keine Geheimnisse, über den Actuator ohnehin nur
 * {@code when-authorized} sichtbar, siehe application.yml).
 */
@Component
public class OfflineIncidentHealthIndicator implements HealthIndicator {

    private final TerminalOfflineIncidentService incidentService;

    public OfflineIncidentHealthIndicator(TerminalOfflineIncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Override
    public Health health() {
        long openCount = this.incidentService.countOpen();
        Health.Builder builder = openCount == 0 ? Health.up() : Health.status(Status.OUT_OF_SERVICE);
        return builder.withDetail("openCount", openCount).build();
    }
}
