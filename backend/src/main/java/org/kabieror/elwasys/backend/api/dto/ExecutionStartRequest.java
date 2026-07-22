package org.kabieror.elwasys.backend.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Entspricht {@code DataManager#newExecution} + {@code ExecutionManager#startExecution} im
 * Alt-Code (persistenzseitig - die hardwarenahe Ansteuerung der Steckdose bleibt im
 * Terminal, siehe {@link org.kabieror.elwasys.backend.service.ExecutionService}
 * Klassen-Javadoc).
 *
 * <p>{@code clientTimestamp} (AP3, Phase 4, additiv, optional): der Original-Zeitstempel des
 * Terminals, zu dem die Ausführung tatsächlich begonnen hat - wichtig für die spätere
 * Offline-Nachmeldung (AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen
 * am Terminal"), bei der zwischen Ereigniszeitpunkt und Übertragungszeitpunkt Zeit vergangen
 * sein kann. Fehlt das Feld (bzw. ist es {@code null}), verwendet der Server wie bisher
 * seine eigene Uhr ({@code LocalDateTime.now()}) - bestehende Aufrufer ohne dieses Feld
 * verhalten sich unverändert.
 */
public record ExecutionStartRequest(@NotNull Integer userId, @NotNull Integer deviceId, @NotNull Integer programId,
        LocalDateTime clientTimestamp) {
}
