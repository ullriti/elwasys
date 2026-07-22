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
 *
 * <p>{@code replay} (Issue #16, optional): kennzeichnet eine <b>Nachmeldung</b> einer bereits
 * offline gebuchten Ausführung (privilegierter Nachbuchungs-Pfad). Ist das Feld {@code true},
 * überspringt {@link org.kabieror.elwasys.backend.api.ExecutionController#start} die
 * fachlichen Wächter (Sperrung/Standort/Nutzbarkeit/Belegung/Guthaben) - das Ereignis ist ein
 * <b>Fakt</b>, keine Anfrage, und die Auftraggeber-Festlegung (siehe
 * docs/kb/05-migration-plan.md und ADR 0010) sieht ausdrücklich vor, dass der Snapshot-Stand
 * gilt und negativ gewordene Salden beim Replay normal verbucht werden. Fehlt das Feld (bzw.
 * ist es {@code null}/{@code false}), verhält sich der Endpunkt exakt wie eine Live-Buchung.
 */
public record ExecutionStartRequest(@NotNull Integer userId, @NotNull Integer deviceId, @NotNull Integer programId,
        LocalDateTime clientTimestamp, Boolean replay) {

    /**
     * Ob dies eine Offline-Nachmeldung ist (privilegierter Nachbuchungs-Pfad, Issue #16) -
     * {@code null} (Feld fehlt) wie {@code false} behandelt.
     */
    public boolean isReplay() {
        return Boolean.TRUE.equals(this.replay);
    }
}
