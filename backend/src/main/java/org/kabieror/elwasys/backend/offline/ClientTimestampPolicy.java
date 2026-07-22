package org.kabieror.elwasys.backend.offline;

import java.time.Duration;
import java.time.LocalDateTime;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.service.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prüft vom Terminal mitgeschickte Original-Zeitstempel ({@code clientTimestamp}, seit
 * Phase 4 AP3 auf den Execution-Endpunkten) gegen das erlaubte Zeitfenster, bevor
 * {@link org.kabieror.elwasys.backend.api.ExecutionController} sie an
 * {@code ExecutionService} weiterreicht (Phase 4 AP6, siehe docs/kb/05-migration-plan.md
 * "Konzeptskizze: Offline-Buchungen am Terminal" Punkt 4 "Nachmeldung (Replay)" und
 * "Festlegungen zu den Offline-Detailfragen").
 *
 * <p><b>Zeitfenster</b>: ein Original-Zeitstempel wird akzeptiert, wenn er innerhalb
 * {@code [jetzt - (offline.max-duration des Standorts + Uhren-Drift-Toleranz), jetzt +
 * Uhren-Drift-Toleranz]} liegt. Das deckt sowohl "normale" Uhren-Abweichungen (Toleranz
 * allein) als auch eine Nachmeldung ab, die bis zu {@code offline.max-duration} in der
 * Vergangenheit liegt (ein Terminal darf laut Konzept während des gesamten Zeitfensters
 * offline Buchungen sammeln und sie erst am Ende nachmelden). Außerhalb dieses Fensters
 * verwendet der Server stattdessen seine eigene Uhrzeit ({@link #resolve}) und protokolliert
 * den Vorfall (Auftraggeber-Vorgabe: "sonst Server-Zeit + Protokollhinweis").
 *
 * <p><b>Benachrichtigungen</b> ({@link #isNotificationSuppressed}): unabhängig vom obigen
 * Toleranzfenster werden Benachrichtigungen zu nachgemeldeten Ereignissen NICHT verschickt,
 * wenn das Ereignis selbst älter als {@code offline.max-duration} ist (Auftraggeber-Vorgabe:
 * "Ereignisse, die älter sind als die maximale Offline-Dauer, werden ohne Versand
 * verbucht.") - absichtlich OHNE die zusätzliche Uhren-Drift-Toleranz, da es hier nicht um
 * Uhren-Ungenauigkeit geht, sondern um die fachliche Frage, ob eine Benachrichtigung nach so
 * langer Zeit noch sinnvoll ist.
 */
@Service
public class ClientTimestampPolicy {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final OfflineProperties properties;

    public ClientTimestampPolicy(OfflineProperties properties) {
        this.properties = properties;
    }

    /**
     * Liefert den zu persistierenden Zeitstempel: den unveränderten {@code clientTimestamp},
     * wenn er innerhalb des erlaubten Zeitfensters liegt, sonst die aktuelle Serverzeit
     * (mit Protokollhinweis). {@code null} bleibt {@code null} (kein clientseitiger
     * Zeitstempel mitgeschickt - Aufrufer verwenden dafür wie bisher
     * {@code LocalDateTime.now()}, siehe {@code ExecutionService}).
     */
    public LocalDateTime resolve(LocalDateTime clientTimestamp, LocationEntity location, String operation) {
        if (clientTimestamp == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        Duration tolerance = this.properties.getClockDriftTolerance();
        int maxDurationMinutes = effectiveMaxDurationMinutes(location);
        LocalDateTime earliestAccepted = now.minusMinutes(maxDurationMinutes).minus(tolerance);
        LocalDateTime latestAccepted = now.plus(tolerance);

        if (clientTimestamp.isBefore(earliestAccepted) || clientTimestamp.isAfter(latestAccepted)) {
            this.logger.warn(
                    "Client-Zeitstempel {} fuer Vorgang '{}' am Standort '{}' liegt ausserhalb des erlaubten "
                            + "Fensters [{}, {}] (offline.max-duration={}min, Toleranz={}) - verwende stattdessen "
                            + "die Serverzeit {}.", clientTimestamp, operation, location.getName(), earliestAccepted,
                    latestAccepted, maxDurationMinutes, tolerance, now);
            return now;
        }
        return clientTimestamp;
    }

    /**
     * Ob eine Benachrichtigung zu einem mit diesem Original-Zeitstempel gemeldeten Ereignis
     * unterdrückt werden soll, weil das Ereignis älter als {@code offline.max-duration} des
     * Standorts ist (siehe Klassen-Javadoc). {@code null} (kein Original-Zeitstempel, d.h.
     * ein "normaler", nicht nachgemeldeter Aufruf) unterdrückt nie.
     */
    public boolean isNotificationSuppressed(LocalDateTime clientTimestamp, LocationEntity location) {
        if (clientTimestamp == null) {
            return false;
        }
        int maxDurationMinutes = effectiveMaxDurationMinutes(location);
        Duration age = Duration.between(clientTimestamp, LocalDateTime.now());
        return age.compareTo(Duration.ofMinutes(maxDurationMinutes)) > 0;
    }

    private static int effectiveMaxDurationMinutes(LocationEntity location) {
        Integer configured = location.getOfflineMaxDurationMinutes();
        return configured != null ? configured : LocationService.DEFAULT_OFFLINE_MAX_DURATION_MINUTES;
    }
}
