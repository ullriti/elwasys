package org.kabieror.elwasys.backend.ws;

/**
 * Wird von {@link TerminalMaintenanceService} geworfen, wenn eine Fernwartungs-Anfrage
 * (Log/Neustart) an einen Standort gerichtet wird, dessen Terminal aktuell keine
 * WebSocket-Verbindung zum Backend hält (siehe {@link TerminalConnectionRegistry#isConnected}).
 * Die Portal-UI zeigt dafür den in der Aufgabenstellung geforderten klaren Zustand ("Terminal
 * nicht verbunden") statt eine Anfrage zu verschicken, die ohnehin nie beantwortet würde.
 */
public class TerminalNotConnectedException extends RuntimeException {

    public TerminalNotConnectedException(Integer locationId) {
        super("Terminal an Standort " + locationId + " ist aktuell nicht verbunden.");
    }
}
