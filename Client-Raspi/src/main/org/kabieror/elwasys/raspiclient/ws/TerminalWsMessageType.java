package org.kabieror.elwasys.raspiclient.ws;

/**
 * Nachrichtentypen des Terminal-WebSocket-Protokolls (Phase 4 AP5, Client-seitiges Gegenstück
 * zu {@code backend.ws.TerminalWsMessageType} - siehe docs/kb/03-modules.md für die vollständige
 * Protokoll-Doku, dort als Backend-Wahrheit gepflegt). Die Konstantennamen müssen 1:1 mit dem
 * Backend übereinstimmen, da beide Seiten den Typ als JSON-String (Enum-Name) über die
 * Verbindung austauschen.
 */
public enum TerminalWsMessageType {
    /** Terminal -&gt; Server: Verbindungsaufbau, Client-Version/Metadaten. */
    HELLO,
    /** Server -&gt; Terminal: Bestätigung + Standort-/Protokollinformationen. */
    HELLO_ACK,
    /** Beide Richtungen: Heartbeat-Anfrage. */
    PING,
    /** Beide Richtungen: Heartbeat-Antwort. */
    PONG,
    /** Server -&gt; Terminal (portal-initiiert, Phase 4 AP5): Bitte um den aktuellen Status. */
    STATUS_REQUEST,
    /** Terminal -&gt; Server: Antwort auf {@link #STATUS_REQUEST}. */
    STATUS_RESPONSE,
    /** Server -&gt; Terminal (portal-initiiert): Bitte um den aktuellen Log-Inhalt. */
    LOG_REQUEST,
    /** Terminal -&gt; Server: Antwort auf {@link #LOG_REQUEST}, Payload {@code {"lines": [...]}}. */
    LOG_RESPONSE,
    /** Server -&gt; Terminal (portal-initiiert): Bitte, die Anwendung neu zu starten. */
    RESTART_REQUEST,
    /** Terminal -&gt; Server: Bestätigung von {@link #RESTART_REQUEST}. */
    RESTART_RESPONSE,
    /** Beide Richtungen: Protokoll-/Verarbeitungsfehler. */
    ERROR
}
