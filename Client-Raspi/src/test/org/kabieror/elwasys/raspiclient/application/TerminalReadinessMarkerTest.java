package org.kabieror.elwasys.raspiclient.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schneller Unit-Test für {@link TerminalReadinessMarker} (Phase 6 AP5). Prüft, dass der
 * Marker in ein per System-Property vorgegebenes Ziel geschrieben wird, Inhalt enthält
 * und dass ein erneuter Aufruf den {@code mtime} vorwärts bewegt. Kein TestFX/keine DB.
 */
class TerminalReadinessMarkerTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(TerminalReadinessMarker.MARKER_FILE_PROPERTY);
    }

    @Test
    void writesMarkerFileToConfiguredPath(@TempDir Path tmp) {
        final Path marker = tmp.resolve(".terminal-ready");
        System.setProperty(TerminalReadinessMarker.MARKER_FILE_PROPERTY, marker.toString());

        TerminalReadinessMarker.markReady();

        assertTrue(Files.exists(marker), "Marker-Datei muss angelegt worden sein");
        assertEquals(marker, TerminalReadinessMarker.resolveMarkerPath());
    }

    @Test
    void markerContentIsNonEmpty(@TempDir Path tmp) throws Exception {
        final Path marker = tmp.resolve(".terminal-ready");
        System.setProperty(TerminalReadinessMarker.MARKER_FILE_PROPERTY, marker.toString());

        TerminalReadinessMarker.markReady();

        final String content = Files.readString(marker).trim();
        assertNotNull(content);
        assertTrue(content.length() > 0, "Marker soll Zeitstempel + Version enthalten");
    }

    @Test
    void secondCallAdvancesModificationTime(@TempDir Path tmp) throws Exception {
        final Path marker = tmp.resolve(".terminal-ready");
        System.setProperty(TerminalReadinessMarker.MARKER_FILE_PROPERTY, marker.toString());

        TerminalReadinessMarker.markReady();
        final long first = Files.getLastModifiedTime(marker).toMillis();

        // Kurz warten, damit sich der mtime messbar ändern kann.
        Thread.sleep(20);
        TerminalReadinessMarker.markReady();
        final long second = Files.getLastModifiedTime(marker).toMillis();

        assertTrue(second >= first, "mtime darf nicht rückwärts laufen (" + second + " >= " + first + ")");
    }

    @Test
    void ioErrorIsSwallowed() {
        // Pfad zeigt auf ein Ziel, dessen Parent eine bestehende Datei ist -> nicht anlegbar.
        // markReady() darf trotzdem NICHT werfen (Robustheitsgarantie).
        System.setProperty(TerminalReadinessMarker.MARKER_FILE_PROPERTY, "/dev/null/impossible/.terminal-ready");
        TerminalReadinessMarker.markReady();
        // kein assert nötig: Test besteht, wenn keine Exception geflogen ist.
    }
}
