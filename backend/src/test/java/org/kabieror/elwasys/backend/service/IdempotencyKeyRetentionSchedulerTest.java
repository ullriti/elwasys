package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalIdempotencyKeyEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.TerminalIdempotencyKeyRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regressionstest für den Retention/Purge-Job der Tabelle {@code terminal_idempotency_keys}
 * (Issue #32 - Betriebskonzept Dauerbetrieb, siehe {@link IdempotencyKeyRetentionScheduler}).
 *
 * <p>Die Zeilen werden regulär per JPA geschrieben (damit die {@code @Lob}-Spalte
 * {@code response_body} konsistent wie in der Produktion gemappt wird) und ihr {@code created_at}
 * anschließend per {@link JdbcTemplate} auf einen EXPLIZITEN Zeitpunkt zurückdatiert (der
 * Entity-Konstruktor setzt {@code created_at} sonst auf {@code now()} - für einen
 * deterministischen Alt/Neu-Vergleich muss der Zeitpunkt aber frei setzbar sein). Kein
 * {@code sleep}/Zufall: die Löschgrenze wird ebenfalls als fester Zeitpunkt vorgegeben.
 */
class IdempotencyKeyRetentionSchedulerTest extends AbstractBackendIT {

    @Autowired
    private IdempotencyKeyRetentionScheduler scheduler;

    @Autowired
    private TerminalIdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String insertKey(LocationEntity location, LocalDateTime createdAt) {
        String key = Fixtures.unique("idem");
        // Regulär per JPA anlegen (konsistentes @Lob-Mapping), danach created_at zurückdatieren.
        this.idempotencyKeyRepository.save(new TerminalIdempotencyKeyEntity(key, location, "finish", 200, "{}"));
        this.jdbcTemplate.update("UPDATE terminal_idempotency_keys SET created_at = ? WHERE idempotency_key = ?",
                Timestamp.valueOf(createdAt), key);
        return key;
    }

    /**
     * Existenzprüfung per JDBC-Count statt {@code findByIdempotencyKey}: die {@code @Lob}-Spalte
     * {@code response_body} wird auf PostgreSQL als Large Object gemappt, dessen Stream nur in
     * DERSELBEN Transaktion lesbar ist wie der Schreibvorgang - der bewusst transaktionslose
     * {@link AbstractBackendIT} (jeder Repository-Aufruf committet für sich) kann ihn also nicht
     * nachladen. Der Count berührt {@code response_body} nicht.
     */
    private boolean exists(String key) {
        Integer count = this.jdbcTemplate.queryForObject(
                "SELECT count(*) FROM terminal_idempotency_keys WHERE idempotency_key = ?", Integer.class, key);
        return count != null && count > 0;
    }

    @Test
    void purgeOlderThanDeletesEntriesBeforeThresholdAndKeepsNewerOnes() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));

        // Drei Einträge mit fest vorgegebenen Zeitstempeln.
        String oldKey = insertKey(location, LocalDateTime.of(2020, 1, 1, 0, 0));
        String midKey = insertKey(location, LocalDateTime.of(2020, 6, 1, 0, 0));
        String newKey = insertKey(location, LocalDateTime.of(2020, 12, 1, 0, 0));

        // Löschgrenze zwischen mid und new: old + mid müssen fallen, new bleibt.
        int removed = this.scheduler.purgeOlderThan(LocalDateTime.of(2020, 7, 1, 0, 0));

        assertThat(removed).isEqualTo(2);
        assertThat(exists(oldKey)).isFalse();
        assertThat(exists(midKey)).isFalse();
        assertThat(exists(newKey)).as("Einträge jünger als die Löschgrenze bleiben erhalten").isTrue();
    }

    @Test
    void purgeExpiredKeysUsesConfiguredRetentionRelativeToNow() {
        // Verdrahtungstest des geplanten Einstiegspunkts: der Job berechnet die Grenze aus der
        // konfigurierten Aufbewahrungsfrist (Default 30 Tage). Ein weit in der Vergangenheit
        // liegender Eintrag ist zwangsläufig älter, ein soeben angelegter zwangsläufig jünger -
        // deterministisch ohne Abhängigkeit von der Wanduhr-Auflösung.
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        String ancientKey = insertKey(location, LocalDateTime.of(2000, 1, 1, 0, 0));
        String freshKey = insertKey(location, LocalDateTime.now());

        this.scheduler.purgeExpiredKeys();

        assertThat(exists(ancientKey)).as("weit über der Aufbewahrungsfrist -> gelöscht").isFalse();
        assertThat(exists(freshKey)).as("gerade erst angelegt -> bleibt erhalten").isTrue();
    }
}
