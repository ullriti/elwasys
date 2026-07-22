package org.kabieror.elwasys.raspiclient.application;

import org.kabieror.elwasys.common.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Schreibt einen "Readiness-Marker" auf die Platte, sobald das Terminal seinen
 * bedienbereiten Zustand ({@link org.kabieror.elwasys.raspiclient.ui.MainFormState#SELECT_DEVICE})
 * erreicht (u.a. beim frischen App-Start STARTUP -> SELECT_DEVICE).
 *
 * <p>Zweck (Phase 6 AP5, siehe docs/kb/05-migration-plan.md und
 * deploy/terminal/auto-update-watchdog.sh): Der Shell-Watchdog auf dem Pi wertet den
 * <b>mtime</b> dieser Datei aus, um festzustellen, ob eine gerade ausgerollte
 * Client-Version tatsächlich hochgekommen und bedienbereit ist. Bleibt der mtime nach
 * einem Update aus, rollt der Watchdog auf die vorige Version zurück.
 *
 * <p>Der Marker ist ein reiner Nebeneffekt-Schreiber und darf den Bedienfluss NIE
 * beeinträchtigen: Jeder IO-Fehler wird gefangen und nur geloggt, nie in die UI
 * geworfen.
 *
 * <p>Pfad-Ermittlung: System-Property {@code elwasys.readyMarkerFile}, sonst der Default
 * {@code ${user.dir}/.terminal-ready}. Auf dem Gerät ist {@code user.dir} das
 * ELWA_ROOT (der von {@code setup.sh} generierte {@code run.sh} macht {@code cd
 * $ELWA_ROOT}), sodass der Default {@code /opt/elwasys/.terminal-ready} ist - genau der
 * Pfad, den der Watchdog per {@code ELWA_MARKER_FILE} standardmäßig prüft.
 */
public final class TerminalReadinessMarker {

    /** System-Property zum Überschreiben des Marker-Pfads (Tests/Sonderfälle). */
    public static final String MARKER_FILE_PROPERTY = "elwasys.readyMarkerFile";

    /** Default-Dateiname relativ zu {@code user.dir}. */
    static final String DEFAULT_MARKER_FILENAME = ".terminal-ready";

    private static final Logger logger = LoggerFactory.getLogger(TerminalReadinessMarker.class);

    private TerminalReadinessMarker() {
        // Nur statische Nutzung.
    }

    /**
     * Ermittelt den Marker-Pfad: System-Property {@code elwasys.readyMarkerFile}, sonst
     * {@code ${user.dir}/.terminal-ready}.
     */
    public static Path resolveMarkerPath() {
        final String override = System.getProperty(MARKER_FILE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        final String userDir = System.getProperty("user.dir", ".");
        return Paths.get(userDir, DEFAULT_MARKER_FILENAME);
    }

    /**
     * Aktualisiert den Readiness-Marker: schreibt den Inhalt (Zeitstempel + App-Version)
     * neu und setzt damit einen frischen {@code mtime}.
     *
     * <p>Robust: Fängt jeden {@link IOException} (und jede sonstige Laufzeitausnahme) ab
     * und loggt sie nur - der Aufrufer (State-Manager) läuft immer ungestört weiter.
     */
    public static void markReady() {
        try {
            final Path marker = resolveMarkerPath();
            final Instant now = Instant.now();
            final String content = now.toString() + " " + Utilities.APP_VERSION + System.lineSeparator();
            // Übergeordnetes Verzeichnis normalerweise vorhanden (ELWA_ROOT); zur
            // Sicherheit dennoch anlegen, falls ein Override in ein Unterverzeichnis zeigt.
            final Path parent = marker.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(marker, content.getBytes(StandardCharsets.UTF_8));
            // mtime explizit auf jetzt setzen - so ist ein Fortschritt auch dann sichtbar,
            // wenn der Inhalt identisch bliebe.
            Files.setLastModifiedTime(marker, java.nio.file.attribute.FileTime.from(now));
            logger.trace("Readiness-Marker aktualisiert: {}", marker);
        } catch (final Exception e) {
            // NIE in die UI werfen - nur loggen.
            logger.warn("Readiness-Marker konnte nicht geschrieben werden: {}", e.toString());
        }
    }
}
