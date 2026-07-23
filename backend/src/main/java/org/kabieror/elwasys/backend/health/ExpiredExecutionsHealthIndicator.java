package org.kabieror.elwasys.backend.health;

import org.kabieror.elwasys.backend.service.ExecutionService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * Betrieblicher Health-Indicator (Issue #32 - Betriebskonzept Dauerbetrieb, Alerting-Grundlage):
 * meldet offene, abgelaufene, aber noch nicht abgerechnete Ausführungen. Eine über
 * {@code maxDuration} hinaus gelaufene, nicht abgeschlossene Ausführung (siehe
 * {@link ExecutionService#getAllExpiredExecutions()}) wird nie automatisch abgerechnet und
 * blockiert das Guthaben des Nutzers (Issue #60), bis ein Admin sie abräumt - ein sonst nur
 * durch Nutzerbeschwerden sichtbares stilles Fehlerbild.
 *
 * <p>Bei mindestens einer solchen Ausführung ist der Status {@link Status#OUT_OF_SERVICE} (der
 * Backend-Prozess selbst ist gesund, es besteht aber operativer Handlungsbedarf), sonst
 * {@link Status#UP}. Das Detail {@code count} nennt die Anzahl - keine Geheimnisse, über den
 * Actuator nur {@code when-authorized} sichtbar (siehe application.yml).
 */
@Component
public class ExpiredExecutionsHealthIndicator implements HealthIndicator {

    private final ExecutionService executionService;

    public ExpiredExecutionsHealthIndicator(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public Health health() {
        int count = this.executionService.getAllExpiredExecutions().size();
        Health.Builder builder = count == 0 ? Health.up() : Health.status(Status.OUT_OF_SERVICE);
        return builder.withDetail("count", count).build();
    }
}
