package org.kabieror.elwasys.backend.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Deterministischer Unit-Test (Mockito, keine DB) für {@link OfflineIncidentHealthIndicator}
 * (Issue #89 - Dead-Letter-Sichtbarkeit): kein offener Vorfall -> {@code UP}, mindestens einer
 * -> {@link Status#OUT_OF_SERVICE} mit Detail {@code openCount}. Quittierte Vorfälle zählen
 * nicht mehr mit (das ist der Rückweg aus dem Alarm, siehe Service).
 */
class OfflineIncidentHealthIndicatorTest {

    private final TerminalOfflineIncidentService incidentService = mock(TerminalOfflineIncidentService.class);

    private final OfflineIncidentHealthIndicator indicator = new OfflineIncidentHealthIndicator(this.incidentService);

    @Test
    void noOpenIncidentsIsUp() {
        when(this.incidentService.countOpen()).thenReturn(0L);

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("openCount", 0L);
    }

    @Test
    void openIncidentsAreOutOfServiceWithCount() {
        when(this.incidentService.countOpen()).thenReturn(3L);

        Health health = this.indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("openCount", 3L);
    }
}
