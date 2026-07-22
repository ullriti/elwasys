package org.kabieror.elwasys.backend.ws;

/**
 * Wird von {@link TerminalMaintenanceService} geworfen, wenn ein verbundenes Terminal auf
 * eine Fernwartungs-Anfrage (Log/Neustart) nicht innerhalb des konfigurierten Zeitfensters
 * antwortet - z.B. weil das verbundene Terminal das Nachrichtenformat (noch) nicht
 * implementiert (die volle terminalseitige Portierung folgt laut docs/kb/05-migration-plan.md erst
 * in Phase 4) oder eine reale Netzwerkstörung zwischen HELLO und Antwort auftrat.
 */
public class TerminalRequestTimeoutException extends RuntimeException {

    public TerminalRequestTimeoutException(Integer locationId) {
        super("Terminal an Standort " + locationId + " hat nicht rechtzeitig geantwortet.");
    }
}
