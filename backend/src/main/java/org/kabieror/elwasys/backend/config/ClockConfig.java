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

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
