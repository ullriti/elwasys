package org.kabieror.elwasys.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regressionstest zum Zonen-Fehler im {@link ClockConfig}-Bean.
 *
 * <p>Der Bean lieferte {@code Clock.systemUTC()}, während die Anwendung ihre Zeitstempel
 * überall sonst mit {@code LocalDateTime.now()} in der Systemzone erzeugt und sie in
 * {@code timestamp without time zone}-Spalten ablegt. Dadurch wichen genau die Felder ab,
 * die über diesen Bean laufen — beobachtet an {@code terminal_tokens}, wo
 * {@code created_at} (Systemzone) und {@code last_used_at} (UTC) in derselben Zeile zwei
 * Stunden auseinanderlagen.
 *
 * <p>Die Tests sind bewusst zonen-agnostisch formuliert: sie vergleichen den Bean gegen die
 * Systemzone, statt eine konkrete Zone (etwa Europe/Berlin) zu erwarten. Sonst wären sie auf
 * einem UTC-Runner grün und träfen die Aussage nicht.
 */
class ClockConfigTest {

    private final Clock clock = new ClockConfig().systemClock();

    @Test
    @DisplayName("Der Clock-Bean läuft in der Systemzone, nicht auf UTC")
    void clockUsesSystemZone() {
        assertThat(this.clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    @DisplayName("LocalDateTime.now(clock) stimmt mit LocalDateTime.now() überein")
    void localDateTimeMatchesDefaultClock() {
        // Der eigentliche Fehler in der Praxis: beide Schreibwege landeten in derselben
        // Tabelle, lieferten aber unterschiedliche Wandzeiten. Sekundengenau vergleichen,
        // damit der Test nicht an der Ausführungsdauer hängt.
        LocalDateTime viaBean = LocalDateTime.now(this.clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime viaDefault = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(java.time.Duration.between(viaBean, viaDefault).abs().getSeconds())
                .as("Bean-Uhr und Standard-Uhr dürfen nicht um einen Zonenversatz auseinanderliegen")
                .isLessThanOrEqualTo(1L);
    }
}
