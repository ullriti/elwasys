package org.kabieror.elwasys.backend.offline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration der Backend-seitigen Offline-Nachmelde-Toleranz (Phase 4 AP6, siehe
 * docs/kb/05-migration-plan.md "Festlegungen zu den Offline-Detailfragen" - "Uhren-Drift:
 * ... Toleranz [konkret: ±5 Minuten, konfigurierbar]"). Terminals laufen mit NTP
 * (Raspbian-Default), Original-Zeitstempel können aber trotzdem leicht abweichen bzw. ein
 * Terminal kann während eines Backend-Ausfalls länger als die konfigurierte
 * {@code offline.max-duration} des Standorts offline gewesen sein - siehe
 * {@link ClientTimestampPolicy} für die Anwendung.
 */
@Component
@ConfigurationProperties(prefix = "elwasys.offline")
public class OfflineProperties {

    /**
     * Toleranz um das eigentliche Zeitfenster (Standort-{@code offline.max-duration} bzw.
     * "jetzt"), innerhalb derer ein vom Terminal mitgeschickter Original-Zeitstempel
     * (Uhren-Drift) noch akzeptiert wird. Default 5 Minuten (Auftraggeber-Vorgabe).
     */
    private Duration clockDriftTolerance = Duration.ofMinutes(5);

    /**
     * Mindestabstand in die Vergangenheit, den der Original-Zeitstempel einer
     * privilegierten Offline-Nachmeldung ({@code replay}, Issue #16) haben MUSS, damit sie
     * angenommen wird (Defense-in-Depth, Issue #67). Eine echte Nachmeldung trägt immer den
     * ORIGINAL-Zeitpunkt der offline gebuchten Ausführung; ein Stufe-B-{@code START} wird
     * zudem erst nachgemeldet, wenn sein Ende bereits im Journal liegt (die Maschine also
     * fertig ist) - der gemeldete Startzeitpunkt liegt damit stets deutlich (Waschdauer +
     * Wiederverbindungszeit) in der Vergangenheit. Ein Replay OHNE oder mit einem "jetzt"-
     * Zeitstempel ist dagegen verdächtig (ein Terminal, das die fachlichen Wächter für eine
     * Live-Buchung umgehen will) und wird abgelehnt. Default 60 Sekunden: groß genug, um
     * "jetzt" sicher abzuweisen, klein genug, um selbst den kürzesten realen Waschgang nie
     * fälschlich abzulehnen. Siehe {@link ClientTimestampPolicy#requireValidReplayTimestamp}.
     */
    private Duration replayMinBackdating = Duration.ofSeconds(60);

    public Duration getClockDriftTolerance() {
        return this.clockDriftTolerance;
    }

    public void setClockDriftTolerance(Duration clockDriftTolerance) {
        this.clockDriftTolerance = clockDriftTolerance;
    }

    public Duration getReplayMinBackdating() {
        return this.replayMinBackdating;
    }

    public void setReplayMinBackdating(Duration replayMinBackdating) {
        this.replayMinBackdating = replayMinBackdating;
    }
}
