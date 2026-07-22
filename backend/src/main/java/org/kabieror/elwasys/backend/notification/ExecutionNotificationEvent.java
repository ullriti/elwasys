package org.kabieror.elwasys.backend.notification;

import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;

/**
 * Signalisiert, dass zu einer beendeten/abgebrochenen Ausführung eine Benutzer-Benachrichtigung
 * (E-Mail/Pushover) versandt werden soll (Issue #36 - AP3, siehe docs/kb/05-migration-plan.md).
 *
 * <p>Wird von {@link org.kabieror.elwasys.backend.api.ExecutionController} INNERHALB der
 * Finish-/Abort-Transaktion publiziert, aber erst nach deren erfolgreichem Commit vom
 * {@link ExecutionNotificationListener} verarbeitet ({@code @TransactionalEventListener},
 * Phase {@code AFTER_COMMIT}). So blockiert ein hängender SMTP-Server nicht mehr die
 * DB-Transaktion (Locks/Verbindungspool) und ein zurückgerollter Aufruf (z.B. wegen eines
 * Fehlers beim Persistieren) löst KEINE "fertig"-Mail zu einer nicht verbuchten Ausführung
 * aus - anders als im bisherigen In-Transaktions-Versand.
 *
 * <p>Die (bereits initialisierten) {@link UserEntity}/{@link DeviceEntity} werden bewusst
 * mitgegeben statt nur ihrer Ids: der Listener liest ausschließlich bereits geladene
 * Basis-Spalten (Name, E-Mail-Opt-in, Pushover-Key, Gerätename), die auch an der detachten
 * Entität nach dem Commit verfügbar sind - kein Nachladen einer Lazy-Assoziation.
 *
 * <p>Die Entscheidung, OB überhaupt benachrichtigt wird (Unterdrückung stark verspäteter
 * Offline-Nachmeldungen, siehe {@code ClientTimestampPolicy}), trifft weiterhin der Controller
 * VOR dem Publizieren - ein unterdrücktes Ereignis wird gar nicht erst veröffentlicht.
 *
 * @param aborted {@code true} für einen Abbruch (andere Nachrichtentexte), {@code false} für
 *                ein reguläres Ende
 */
public record ExecutionNotificationEvent(UserEntity user, DeviceEntity device, boolean aborted) {
}
