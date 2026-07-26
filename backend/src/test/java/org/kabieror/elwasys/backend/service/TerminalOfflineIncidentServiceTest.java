package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalOfflineIncidentEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.TerminalOfflineIncidentRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integrationstests für {@link TerminalOfflineIncidentService} (Issue #89 -
 * Dead-Letter-Sichtbarkeit): Melden, Idempotenz bei wiederholter Meldung und die Quittierung
 * als Rückweg aus dem Alarm.
 */
class TerminalOfflineIncidentServiceTest extends AbstractBackendIT {

    @Autowired
    private TerminalOfflineIncidentService incidentService;

    @Autowired
    private TerminalOfflineIncidentRepository incidentRepository;

    @Autowired
    private LocationRepository locationRepository;

    private LocationEntity newLocation() {
        return this.locationRepository.save(new LocationEntity(Fixtures.unique("incident-loc")));
    }

    private TerminalOfflineIncidentEntity report(LocationEntity location, String incidentKey) {
        return this.incidentService.report(location.getId(), incidentKey,
                TerminalOfflineIncidentEntity.KIND_DEAD_LETTER, "FINISH", "idem-" + incidentKey, null,
                new BigDecimal("1.50"), "Programm geloescht", LocalDateTime.of(2026, 7, 24, 10, 15));
    }

    @Test
    void reportPersistsTheIncidentWithItsAmount() {
        LocationEntity location = newLocation();
        String key = Fixtures.unique("DEAD_LETTER:key");

        TerminalOfflineIncidentEntity saved = report(location, key);

        assertThat(saved.getId()).isNotNull();
        assertThat(this.incidentRepository.findByIncidentKey(key)).isPresent().get()
                .satisfies(incident -> {
                    assertThat(incident.getKind()).isEqualTo(TerminalOfflineIncidentEntity.KIND_DEAD_LETTER);
                    assertThat(incident.getChargedPrice()).isEqualByComparingTo("1.50");
                    assertThat(incident.getReason()).isEqualTo("Programm geloescht");
                    assertThat(incident.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 7, 24, 10, 15));
                    assertThat(incident.isAcknowledged()).isFalse();
                });
    }

    /**
     * Das Terminal kennt den Zustellstatus nicht und meldet nach einem Reconnect/Neustart
     * erneut - das darf keinen Doppel-Eintrag (und damit keinen doppelten Alarm) erzeugen.
     */
    @Test
    void reportingTheSameIncidentTwiceIsIdempotent() {
        LocationEntity location = newLocation();
        String key = Fixtures.unique("DEAD_LETTER:key");

        TerminalOfflineIncidentEntity first = report(location, key);
        long openBefore = this.incidentService.countOpen();
        TerminalOfflineIncidentEntity second = report(location, key);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(this.incidentService.countOpen()).isEqualTo(openBefore);
    }

    @Test
    void acknowledgingClosesTheIncidentButKeepsTheRecord() {
        LocationEntity location = newLocation();
        String key = Fixtures.unique("DEAD_LETTER:key");
        TerminalOfflineIncidentEntity saved = report(location, key);
        long openBefore = this.incidentService.countOpen();

        this.incidentService.acknowledge(saved.getId(), "admin");

        assertThat(this.incidentService.countOpen()).isEqualTo(openBefore - 1);
        assertThat(this.incidentRepository.findByIncidentKey(key)).isPresent().get()
                .satisfies(incident -> {
                    assertThat(incident.isAcknowledged()).isTrue();
                    assertThat(incident.getAcknowledgedBy()).isEqualTo("admin");
                    // Der Beleg bleibt erhalten - der Geldverlust ist quittiert, nicht gelöscht.
                    assertThat(incident.getChargedPrice()).isEqualByComparingTo("1.50");
                });
    }

    /**
     * Ein Terminal, das seine Dead-Letter-Datei behält, könnte einen bereits quittierten
     * Vorfall sonst dauerhaft wiederbeleben und den Alarm nie verstummen lassen.
     */
    @Test
    void reReportingAnAcknowledgedIncidentDoesNotReopenIt() {
        LocationEntity location = newLocation();
        String key = Fixtures.unique("DEAD_LETTER:key");
        TerminalOfflineIncidentEntity saved = report(location, key);
        this.incidentService.acknowledge(saved.getId(), "admin");
        long openAfterAck = this.incidentService.countOpen();

        report(location, key);

        assertThat(this.incidentService.countOpen()).isEqualTo(openAfterAck);
        assertThat(this.incidentRepository.findByIncidentKey(key)).isPresent().get()
                .satisfies(incident -> assertThat(incident.isAcknowledged()).isTrue());
    }
}
