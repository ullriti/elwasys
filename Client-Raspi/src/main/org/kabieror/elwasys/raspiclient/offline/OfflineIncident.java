package org.kabieror.elwasys.raspiclient.offline;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein meldenswerter Zwischenfall der Offline-Robustheit (Issue #89): ein in die Dead-Letter-Datei
 * verschobener Journal-Eintrag ({@link #KIND_DEAD_LETTER}) oder eine nicht mehr aufräumbare
 * Geister-Execution ({@link #KIND_GHOST_EXECUTION}).
 *
 * <p><b>Warum überhaupt gemeldet wird</b>: beide Fälle landeten bisher ausschließlich als
 * {@code logger.error} im lokalen Pi-Log und erreichten damit niemanden - ein dead-gelettert
 * Journal-Eintrag ist aber eine verlorene Offline-Buchung (echtes Geld). Die Meldung geht darum
 * über die ohnehin bestehende, ausgehende WebSocket-Verbindung an das Backend (siehe
 * {@link OfflineIncidentOutbox}); die lokalen Log-Zeilen bleiben als Rückfallebene bestehen.
 *
 * @param incidentKey    deterministischer Idempotenz-Anker der Meldung ({@code kind + ":" +
 *                       idempotencyKey}) - das Backend dedupliziert darüber, ein erneutes Senden
 *                       erzeugt also garantiert keinen Doppel-Eintrag und öffnet einen bereits
 *                       quittierten Vorfall nicht wieder. Genau das macht die Wiederholung aus
 *                       der Outbox gefahrlos.
 * @param kind           {@link #KIND_DEAD_LETTER} oder {@link #KIND_GHOST_EXECUTION}
 * @param entryType      der Typ des betroffenen Journal-Eintrags ({@code START}/{@code FINISH}/
 *                       {@code ABORT})
 * @param idempotencyKey der Idempotenz-Schlüssel des betroffenen Journal-Eintrags
 * @param userId         der betroffene Benutzer (für die Zuordnung des Geldbetrags im Portal)
 * @param chargedPrice   der lokal berechnete Betrag des Eintrags, falls vorhanden
 * @param reason         der bereits für die Dead-Letter-Datei gebildete Klartext-Grund
 * @param occurredAt     der Original-Zeitstempel des Ereignisses ({@code clientTimestamp})
 */
public record OfflineIncident(String incidentKey, String kind, String entryType, String idempotencyKey,
        Integer userId, BigDecimal chargedPrice, String reason, LocalDateTime occurredAt) {

    /** Ein Journal-Eintrag wurde dauerhaft nicht angenommen und ist damit verloren. */
    public static final String KIND_DEAD_LETTER = "DEAD_LETTER";

    /** Eine serverseitig angelegte Ausführung konnte nicht kompensierend beendet werden. */
    public static final String KIND_GHOST_EXECUTION = "GHOST_EXECUTION";

    /**
     * Baut die Meldung aus dem betroffenen Journal-Eintrag - der {@code incidentKey} wird dabei
     * deterministisch aus Art und Idempotenz-Schlüssel gebildet, damit dieselbe Meldung bei einem
     * Wiederholungsversuch exakt denselben Schlüssel trägt (Voraussetzung für die serverseitige
     * Deduplizierung).
     */
    public static OfflineIncident of(String kind, OfflineJournalEntry entry, String reason) {
        return new OfflineIncident(kind + ":" + entry.idempotencyKey(), kind, entry.type(), entry.idempotencyKey(),
                entry.userId(), entry.chargedPrice(), reason, entry.clientTimestamp());
    }
}
