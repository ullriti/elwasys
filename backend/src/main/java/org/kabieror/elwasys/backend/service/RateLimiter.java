package org.kabieror.elwasys.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Kleiner, wiederverwendbarer In-Memory-Ratenbegrenzer (fixed window) für die Einzelinstanz
 * (Pre-Launch AP4, Auth &amp; Security, siehe
 * docs/architecture/0018-ap4-auth-security-entscheidungen.md).
 *
 * <p><b>Warum eine eigene, minimale Lösung (kein bucket4j):</b> das Backend läuft als
 * Einzelinstanz (siehe docs/kb/05-migration-plan.md) – ein prozesslokaler Zähler genügt, eine
 * zusätzliche Bibliothek wäre unnötiger Ballast. Bewusst gemeinsam genutzt vom
 * Brute-Force-Schutz des Portal-Logins ({@code ElwasysAuthenticationProvider}, Issue #25) und
 * vom Passwort-Reset-Versand-Limit ({@code PasswordResetService}, Issue #24) – ein Konzept,
 * zwei Aufrufer.
 *
 * <p><b>Zeitquelle injizierbar</b> ({@link Clock}): macht die zeitfensterbasierte Logik ohne
 * {@code sleep} deterministisch testbar.
 *
 * <p><b>Speicherverhalten:</b> Einträge eines abgelaufenen Fensters werden beim nächsten
 * Zugriff auf denselben Schlüssel überschrieben; erfolgreich abgeschlossene Vorgänge räumen
 * ihren Schlüssel über {@link #reset(String)} sofort ab. Für die Einzelinstanz mit einer
 * überschaubaren Zahl an Benutzernamen/Adressen ist das ausreichend – ein Hintergrund-Aufräumen
 * wird bewusst nicht eingeführt.
 */
@Component
public class RateLimiter {

    private final Clock clock;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Erhöht den Zähler des Schlüssels im aktuellen Zeitfenster und liefert den neuen Stand.
     * Ist das vorige Fenster (relativ zu seinem Beginn) abgelaufen, beginnt ein frisches
     * Fenster mit Zählerstand 1.
     */
    public synchronized int increment(String key, Duration window) {
        Instant now = this.clock.instant();
        Window w = this.windows.get(key);
        if (w == null || isExpired(w, now, window)) {
            this.windows.put(key, new Window(now));
            return 1;
        }
        w.count++;
        return w.count;
    }

    /**
     * Aktueller Zählerstand des Schlüssels, ohne ihn zu erhöhen; ein abgelaufenes Fenster
     * zählt als 0.
     */
    public synchronized int currentCount(String key, Duration window) {
        Instant now = this.clock.instant();
        Window w = this.windows.get(key);
        if (w == null || isExpired(w, now, window)) {
            return 0;
        }
        return w.count;
    }

    /**
     * Entfernt den Zähler eines Schlüssels (z.B. nach einem erfolgreichen Login, damit ein
     * Benutzer nicht durch frühere Fehlversuche belastet bleibt).
     */
    public synchronized void reset(String key) {
        this.windows.remove(key);
    }

    private static boolean isExpired(Window w, Instant now, Duration window) {
        return Duration.between(w.start, now).compareTo(window) >= 0;
    }

    private static final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
            this.count = 1;
        }
    }
}
