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

    public Duration getClockDriftTolerance() {
        return this.clockDriftTolerance;
    }

    public void setClockDriftTolerance(Duration clockDriftTolerance) {
        this.clockDriftTolerance = clockDriftTolerance;
    }
}
