package org.kabieror.elwasys.raspiclient.offline;

/**
 * Schmale Abstraktion, über die {@link OfflineGateway} einen {@link OfflineIncident} meldet
 * (Issue #89).
 *
 * <p><b>Warum ein Interface statt eines direkten Zugriffs auf den WebSocket-Client</b>: der
 * Melde-Weg (ausgehende WS-Verbindung, {@code application.ElwaManager}) ist eine Singleton-Altlast
 * und würde das rein datei-/API-basierte {@link OfflineGateway} an den gesamten Anwendungs-Kontext
 * koppeln - inklusive der bestehenden Tests, die das Gateway direkt mit {@code (apiClient,
 * snapshotStore, journal)} konstruieren. Der Melde-Weg wird darum von außen verdrahtet
 * ({@link OfflineGateway#setIncidentReporter}); ohne Verdrahtung bleibt es beim
 * {@link #NOOP}-Standard und damit beim bisherigen Verhalten (nur lokales Log).
 */
@FunctionalInterface
public interface OfflineIncidentReporter {

    /**
     * Standard-Implementierung, die nichts tut - der Melde-Pfad ist reine Diagnose und darf nie
     * erzwungen sein (z. B. in Tests oder wenn kein Backend-Kanal konfiguriert ist).
     */
    OfflineIncidentReporter NOOP = incident -> {
    };

    /**
     * Meldet einen Vorfall. Implementierungen müssen fail-safe sein: der Aufrufer ist der
     * Geld-Pfad (Journal-Replay), die Meldung nur Diagnose - sie darf ihn weder abbrechen noch
     * nennenswert verzögern.
     */
    void report(OfflineIncident incident);
}
