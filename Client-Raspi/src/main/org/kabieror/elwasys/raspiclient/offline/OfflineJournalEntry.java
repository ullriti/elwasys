package org.kabieror.elwasys.raspiclient.offline;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ein Eintrag im persistenten Ereignis-Journal ({@link OfflineJournal}, Phase 4 AP6, siehe
 * kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal" Punkt 3
 * "Persistentes Ereignis-Journal").
 *
 * @param type                  {@code "START"}, {@code "FINISH"} oder {@code "ABORT"}
 * @param idempotencyKey        der für DIESEN Vorgang verwendete Idempotenz-Schlüssel (wird
 *                               beim Replay 1:1 als {@code Idempotency-Key}-Header
 *                               mitgeschickt)
 * @param clientTimestamp       der Original-Zeitpunkt des Ereignisses
 * @param userId                der Benutzer (bei START/FINISH/ABORT gleichermaßen gesetzt -
 *                               bei FINISH/ABORT nur informativ, für
 *                               {@link OfflineJournal#computeOfflineDebits()})
 * @param deviceId              nur bei {@code START} gesetzt
 * @param programId             nur bei {@code START} gesetzt
 * @param executionId           nur bei {@code FINISH}/{@code ABORT} EINER BEREITS ECHTEN
 *                               Ausführung gesetzt (Stufe A: sie lief online, das Backend war
 *                               nur beim Beenden nicht erreichbar)
 * @param startIdempotencyKey   nur bei {@code FINISH}/{@code ABORT} EINER OFFLINE ANGELEGTEN,
 *                               NOCH NICHT NACHGEMELDETEN Ausführung gesetzt (Stufe B) -
 *                               referenziert den {@code idempotencyKey} des zugehörigen
 *                               {@code START}-Eintrags; das Replay löst daraus die beim
 *                               Nachmelden des Starts gelernte echte Backend-Id auf
 * @param chargedPrice          bei {@code FINISH}/{@code ABORT} der von diesem Terminal lokal
 *                               berechnete Preis (rein informativ, NIE an das Backend
 *                               geschickt - das Backend berechnet seinen eigenen,
 *                               maßgeblichen Preis beim Replay) - dient ausschließlich dazu,
 *                               das lokal aufgelaufene Offline-Guthaben-Delta
 *                               ({@link OfflineJournal#computeOfflineDebits()}) neustartfest
 *                               nachzuvollziehen, ohne eine zweite Datei zu pflegen
 */
public record OfflineJournalEntry(String type, String idempotencyKey, LocalDateTime clientTimestamp, Integer userId,
        Integer deviceId, Integer programId, Integer executionId, String startIdempotencyKey,
        BigDecimal chargedPrice) {

    static final String TYPE_START = "START";
    static final String TYPE_FINISH = "FINISH";
    static final String TYPE_ABORT = "ABORT";
}
