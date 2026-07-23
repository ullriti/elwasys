package org.kabieror.elwasys.backend.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Vorrückbare Test-Zeitquelle: erlaubt zeitfensterbasierte Logik (Login-Sperre,
 * Reset-Ratenlimit, {@code last_used_at}-Drosselung) deterministisch ohne {@code sleep} zu
 * testen (Pre-Launch AP4, Auth &amp; Security). Produktiv kommt stattdessen der
 * {@code systemClock}-Bean zum Einsatz (siehe {@code ClockConfig}).
 */
public final class MutableClock extends Clock {

    private Instant instant;

    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Rückt die Uhr um die angegebene Dauer vor. */
    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return this.zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(this.instant, newZone);
    }

    @Override
    public Instant instant() {
        return this.instant;
    }
}
