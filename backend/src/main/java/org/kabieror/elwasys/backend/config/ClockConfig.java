package org.kabieror.elwasys.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt eine zentrale, injizierbare Zeitquelle bereit (Pre-Launch AP4, Auth &amp; Security,
 * siehe docs/architecture/0018-ap4-auth-security-entscheidungen.md).
 *
 * <p><b>Warum ein {@link Clock}-Bean:</b> die neuen Sicherheits-Härtungen mit Zeitbezug
 * (Brute-Force-Sperre beim Portal-Login, Passwort-Reset-Ratenlimit, Drosselung von
 * {@code terminal_tokens.last_used_at}) müssen deterministisch – also ohne {@code sleep} –
 * testbar sein. Produktiv liefert dieser Bean die echte Uhr; Tests konstruieren die
 * betroffenen Komponenten mit einer vorstellbaren/vorrückbaren Uhr.
 */
@Configuration
public class ClockConfig {

    /**
     * <b>Systemzone, NICHT UTC.</b> Die Anwendung schreibt ihre Zeitstempel als
     * {@link java.time.LocalDateTime} in {@code timestamp without time zone}-Spalten und
     * erzeugt sie überall sonst mit {@code LocalDateTime.now()}, also in der Systemzone
     * (im Betrieb {@code TZ=Europe/Berlin}). Ein auf UTC festgelegter Clock ließe genau die
     * Felder, die über diesen Bean laufen, um den UTC-Versatz abweichen — beobachtet an
     * {@code terminal_tokens}, wo {@code created_at} (17:20, Systemzone) und
     * {@code last_used_at} (16:25, UTC) in derselben Zeile zwei Stunden auseinanderlagen.
     *
     * <p>Für {@link org.kabieror.elwasys.backend.service.RateLimiter} ist die Zone
     * gleichgültig — er rechnet mit {@code clock.instant()}. Tests injizieren ohnehin eine
     * eigene, vorrückbare Uhr und bleiben unberührt.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
